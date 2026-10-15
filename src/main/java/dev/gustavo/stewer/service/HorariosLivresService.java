package dev.gustavo.stewer.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.Bloqueio;
import dev.gustavo.stewer.domain.Disponibilidade;
import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.dto.agendamento.HorarioLivreResponse;
import dev.gustavo.stewer.dto.agendamento.HorariosLivresResponse;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.repository.AgendamentoRepository;
import dev.gustavo.stewer.repository.BloqueioRepository;
import dev.gustavo.stewer.repository.DisponibilidadeRepository;
import dev.gustavo.stewer.repository.ProfissionalRepository;
import dev.gustavo.stewer.repository.ServicoRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Calcula os horarios livres de um profissional em um dia.
 *
 * <p>E o coracao do sistema. A conta e sempre a mesma:
 * <ol>
 *   <li>pega a grade semanal do dia da semana pedido;</li>
 *   <li>subtrai os bloqueios que tocam a janela;</li>
 *   <li>subtrai os agendamentos que ocupam a agenda -- so status
 *       {@code AGENDADO}; cancelado, concluido e nao-comparecimento liberam
 *       o horario;</li>
 *   <li>fatia o que sobrou pela duracao do servico, descartando slot que nao
 *       caiba inteiro na faixa.</li>
 * </ol>
 *
 * <p>Todo intervalo aqui e MEIO-ABERTO: {@code [inicio, fim)}. Dois intervalos
 * colidem quando {@code a.inicio < b.fim && a.fim > b.inicio} -- ou seja,
 * atendimento que termina 10:00 e outro que comeca 10:00 nao conflitam. Essa
 * regra vale identica no repositorio, no agendamento e aqui; se algum dos tres
 * divergir, aparece slot fantasma.
 *
 * <p><strong>Fuso:</strong> a grade semanal e guardada como hora do dia
 * ({@code LocalTime}) e interpretada no fuso do ESTABELECIMENTO
 * ({@code tenant.fuso}, ex.: {@code America/Araguaina}). "09:00" quer dizer
 * nove da manha no relogio de quem atende, em qualquer epoca do ano -- o
 * {@code ZoneId} resolve o offset correto para cada data. O mesmo fuso define
 * onde comeca e termina o dia de calendario pedido em {@code ?data=}.
 *
 * <p>Instantes ({@code Agendamento.inicio}, {@code Bloqueio.inicio}) continuam
 * absolutos e nao passam por conversao nenhuma: so a hora de parede precisa de
 * fuso para virar instante.
 */
@ApplicationScoped
public class HorariosLivresService {

    @Inject
    ProfissionalRepository profissionalRepository;

    @Inject
    ServicoRepository servicoRepository;

    @Inject
    DisponibilidadeRepository disponibilidadeRepository;

    @Inject
    BloqueioRepository bloqueioRepository;

    @Inject
    AgendamentoRepository agendamentoRepository;

    @Inject
    TenantService tenantService;

    /**
     * Intervalo meio-aberto usado nos calculos internos.
     *
     * <p>Existe para que bloqueio e agendamento -- que nao tem tipo comum --
     * possam ser tratados como a mesma coisa: "tempo que nao esta disponivel".
     */
    public record Janela(OffsetDateTime inicio, OffsetDateTime fim) {

        public boolean colideCom(Janela outra) {
            return inicio.isBefore(outra.fim()) && fim.isAfter(outra.inicio());
        }

        public boolean contem(Janela menor) {
            return !menor.inicio().isBefore(inicio) && !menor.fim().isAfter(fim);
        }
    }

    @Transactional
    public HorariosLivresResponse calcular(UUID tenantId, UUID profissionalId, LocalDate data,
            UUID servicoId) {
        Profissional profissional = exigirProfissionalAtivo(tenantId, profissionalId);
        Servico servico = exigirServicoAtivo(tenantId, servicoId);

        List<Janela> livres = janelasLivres(tenantId, profissional.getId(), data,
                servico.duracao());

        List<HorarioLivreResponse> horarios = livres.stream()
                .map(j -> new HorarioLivreResponse(j.inicio(), j.fim()))
                .toList();

        return new HorariosLivresResponse(data, profissional.getId(), servico.getId(),
                servico.getDuracaoMinutos(), horarios);
    }

    /**
     * Slots livres de {@code duracao} no dia, ja ordenados por inicio.
     *
     * <p>Dia sem nenhuma faixa na grade devolve lista vazia -- e uma resposta
     * legitima ("nao atende nesse dia"), nao um erro.
     */
    public List<Janela> janelasLivres(UUID tenantId, UUID profissionalId, LocalDate data,
            Duration duracao) {
        short diaSemana = Disponibilidade.toDiaSemana(data.getDayOfWeek());
        List<Disponibilidade> faixas =
                disponibilidadeRepository.doDia(tenantId, profissionalId, diaSemana);
        if (faixas.isEmpty()) {
            return List.of();
        }

        ZoneId fuso = fusoDo(tenantId);
        List<Janela> ocupacoes = ocupacoesDoDia(tenantId, profissionalId, data);
        // Slot que ja passou nao serve para ninguem reservar; so importa quando
        // a data pedida e hoje, entao a comparacao e feita uma vez so.
        // "Agora" e um instante: nao depende de fuso nenhum.
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);

        List<Janela> livres = new ArrayList<>();
        for (Disponibilidade faixa : faixas) {
            fatiar(new Janela(inicioDaFaixa(data, faixa, fuso), fimDaFaixa(data, faixa, fuso)),
                    duracao, ocupacoes, agora, livres);
        }
        livres.sort(Comparator.comparing(Janela::inicio));
        return livres;
    }

    /**
     * Tudo que impede um atendimento no dia: bloqueios pontuais e agendamentos
     * que ocupam a agenda. A janela consultada e o dia inteiro no fuso do
     * ESTABELECIMENTO, e nao apenas o horario da grade, porque um bloqueio pode
     * comecar na vespera e invadir a manha seguinte.
     */
    public List<Janela> ocupacoesDoDia(UUID tenantId, UUID profissionalId, LocalDate data) {
        ZoneId fuso = fusoDo(tenantId);
        OffsetDateTime inicioDoDia = data.atStartOfDay(fuso).toOffsetDateTime();
        OffsetDateTime fimDoDia = data.plusDays(1).atStartOfDay(fuso).toOffsetDateTime();

        List<Janela> ocupacoes = new ArrayList<>();
        for (Bloqueio bloqueio : bloqueioRepository.naJanela(tenantId, profissionalId,
                inicioDoDia, fimDoDia)) {
            ocupacoes.add(new Janela(bloqueio.getInicio(), bloqueio.getFim()));
        }
        for (Agendamento agendamento : agendamentoRepository.ativosNaJanela(tenantId,
                profissionalId, inicioDoDia, fimDoDia)) {
            ocupacoes.add(new Janela(agendamento.getInicio(), agendamento.getFim()));
        }
        return ocupacoes;
    }

    /**
     * Verifica se um intervalo cabe na grade do dia. Usado pelo agendamento para
     * recusar horario fora do expediente sem precisar montar a lista de slots.
     *
     * <p>O dia e o dia da semana saem do calendario do ESTABELECIMENTO: um
     * atendimento as 22:00 de segunda em Palmas chega como 01:00Z de terca, e
     * lido em UTC bateria contra a grade do dia errado.
     */
    public boolean cabeNaGrade(UUID tenantId, UUID profissionalId, Janela pedido) {
        ZoneId fuso = fusoDo(tenantId);
        LocalDate dia = pedido.inicio().atZoneSameInstant(fuso).toLocalDate();
        short diaSemana = Disponibilidade.toDiaSemana(dia.getDayOfWeek());

        return disponibilidadeRepository.doDia(tenantId, profissionalId, diaSemana).stream()
                .map(faixa -> new Janela(inicioDaFaixa(dia, faixa, fuso),
                        fimDaFaixa(dia, faixa, fuso)))
                .anyMatch(faixa -> faixa.contem(pedido));
    }

    /** O fuso em que a hora de parede deste estabelecimento deve ser lida. */
    public ZoneId fusoDo(UUID tenantId) {
        return tenantService.porId(tenantId).zoneId();
    }

    /**
     * Percorre a faixa de {@code duracao} em {@code duracao}, guardando os
     * pedacos que estao inteiros dentro dela e livres de ocupacao.
     *
     * <p>O passo e a propria duracao do servico: slots ficam encostados, sem
     * buraco entre um e outro. Se um dia a agenda precisar de granularidade fixa
     * (de 15 em 15 minutos, por exemplo), e aqui que o passo muda.
     */
    private static void fatiar(Janela faixa, Duration duracao, List<Janela> ocupacoes,
            OffsetDateTime agora, List<Janela> destino) {
        OffsetDateTime cursor = faixa.inicio();
        while (true) {
            OffsetDateTime fimDoSlot = cursor.plus(duracao);
            if (fimDoSlot.isAfter(faixa.fim())) {
                // O que sobrou da faixa e menor que o servico: nao vira slot.
                return;
            }
            Janela slot = new Janela(cursor, fimDoSlot);
            if (!cursor.isBefore(agora) && ocupacoes.stream().noneMatch(slot::colideCom)) {
                destino.add(slot);
            }
            cursor = fimDoSlot;
        }
    }

    /**
     * Hora de parede da grade -> instante, no fuso do estabelecimento.
     *
     * <p>{@code atZone} (e nao {@code atOffset}) de proposito: o offset de um
     * fuso muda com a data quando existe horario de verao, e so o {@code ZoneId}
     * sabe qual vale naquele dia.
     */
    private static OffsetDateTime inicioDaFaixa(LocalDate data, Disponibilidade faixa,
            ZoneId fuso) {
        return data.atTime(faixa.getHoraInicio()).atZone(fuso).toOffsetDateTime();
    }

    private static OffsetDateTime fimDaFaixa(LocalDate data, Disponibilidade faixa, ZoneId fuso) {
        return data.atTime(faixa.getHoraFim()).atZone(fuso).toOffsetDateTime();
    }

    Profissional exigirProfissionalAtivo(UUID tenantId, UUID profissionalId) {
        Profissional profissional = profissionalRepository.buscarVivo(tenantId, profissionalId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Profissional", profissionalId));
        if (!profissional.isAtivo()) {
            throw new RegraNegocioException("PROFISSIONAL_INATIVO",
                    "Este profissional nao esta atendendo no momento");
        }
        return profissional;
    }

    Servico exigirServicoAtivo(UUID tenantId, UUID servicoId) {
        Servico servico = servicoRepository.buscarVivo(tenantId, servicoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Servico", servicoId));
        if (!servico.isAtivo()) {
            throw new RegraNegocioException("SERVICO_INATIVO",
                    "Este servico nao esta disponivel para agendamento");
        }
        return servico;
    }
}
