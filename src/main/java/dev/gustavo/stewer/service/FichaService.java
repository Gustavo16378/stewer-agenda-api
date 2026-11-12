package dev.gustavo.stewer.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.FichaCliente;
import dev.gustavo.stewer.domain.FotoCliente;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.cliente.AniversarianteResponse;
import dev.gustavo.stewer.dto.cliente.ClienteSumidoResponse;
import dev.gustavo.stewer.dto.cliente.FichaRequest;
import dev.gustavo.stewer.dto.cliente.FichaResponse;
import dev.gustavo.stewer.dto.cliente.FotoConteudoResponse;
import dev.gustavo.stewer.dto.cliente.FotoRequest;
import dev.gustavo.stewer.dto.cliente.FotoResponse;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.mapper.ClienteMapper;
import dev.gustavo.stewer.repository.AgendamentoRepository;
import dev.gustavo.stewer.repository.FichaClienteRepository;
import dev.gustavo.stewer.repository.FotoClienteRepository;
import dev.gustavo.stewer.repository.UsuarioRepository;
import dev.gustavo.stewer.security.UsuarioLogado;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * O que a equipe guarda e enxerga de cada cliente para ele voltar: a ficha
 * (aniversario, origem, etiquetas, perfil de visagismo), as fotos de antes e
 * depois, quem faz aniversario no mes e quem sumiu.
 *
 * <p>Mesmas regras de acesso da ClienteService: profissional da equipe viva ou
 * admin, e o cliente precisa ser um CLIENTE vivo do mesmo estabelecimento.
 */
@ApplicationScoped
public class FichaService {

    /** Teto de cada foto ja decodificada. */
    static final int TAMANHO_MAXIMO_FOTO = 1_500_000;

    /** Com uma visita so, "sumido" e passar disto sem voltar. */
    static final int DIAS_SUMIDO_SEM_HISTORICO = 60;

    /** Nunca chama de sumido quem tem menos que isto desde a ultima vez. */
    static final int DIAS_SUMIDO_MINIMO = 21;

    @Inject
    ClienteService clienteService;

    @Inject
    FichaClienteRepository fichaRepository;

    @Inject
    FotoClienteRepository fotoRepository;

    @Inject
    AgendamentoRepository agendamentoRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    TenantService tenantService;

    @Inject
    Auditoria auditoria;

    @Inject
    UsuarioLogado usuarioLogado;

    @Transactional
    public FichaResponse salvarFicha(UUID tenantId, UUID clienteId, FichaRequest dto) {
        clienteService.exigirEquipeViva();
        Usuario cliente = clienteService.exigirCliente(tenantId, clienteId);

        FichaCliente ficha = fichaRepository.doCliente(tenantId, clienteId).orElseGet(() -> {
            FichaCliente nova = new FichaCliente();
            nova.setTenantId(tenantId);
            nova.setCliente(cliente);
            return nova;
        });
        ficha.setDataNascimento(dto.dataNascimento());
        ficha.setOrigem(texto(dto.origem()));
        ficha.setEtiquetas(etiquetas(dto.etiquetas()));
        ficha.setFormatoRosto(texto(dto.formatoRosto()));
        ficha.setCabelo(texto(dto.cabelo()));
        ficha.setBarba(texto(dto.barba()));
        ficha.setEstilo(texto(dto.estilo()));
        ficha.setManter(texto(dto.manter()));
        ficha.setEvitar(texto(dto.evitar()));
        fichaRepository.persistAndFlush(ficha);

        auditoria.registrar(tenantId, Auditoria.Acao.ALTEROU, Auditoria.Entidade.FICHA, ficha.getId(),
                clienteId, "Atualizou a ficha de " + cliente.getNome());
        return ClienteMapper.paraFicha(ficha);
    }

    @Transactional
    public List<FotoResponse> listarFotos(UUID tenantId, UUID clienteId) {
        clienteService.exigirEquipeViva();
        clienteService.exigirCliente(tenantId, clienteId);
        return fotoRepository.doCliente(tenantId, clienteId).stream().map(FichaService::paraResponse).toList();
    }

    @Transactional
    public FotoConteudoResponse conteudoDaFoto(UUID tenantId, UUID clienteId, UUID fotoId) {
        clienteService.exigirEquipeViva();
        FotoCliente foto = exigirFoto(tenantId, clienteId, fotoId);
        return new FotoConteudoResponse(foto.getId(),
                ImagemRecebida.dataUrl(foto.getTipoConteudo(), foto.getConteudo()));
    }

    @Transactional
    public FotoResponse adicionarFoto(UUID tenantId, UUID clienteId, FotoRequest dto) {
        clienteService.exigirEquipeViva();
        Usuario cliente = clienteService.exigirCliente(tenantId, clienteId);

        ImagemRecebida.Imagem imagem = ImagemRecebida.decodificar(dto.tipoConteudo(), dto.conteudoBase64(),
                TAMANHO_MAXIMO_FOTO, "1,5 MB");

        FotoCliente foto = new FotoCliente();
        foto.setTenantId(tenantId);
        foto.setCliente(cliente);
        foto.setAgendamento(resolverAgendamento(tenantId, clienteId, dto.agendamentoId()));
        foto.setAutor(usuarioRepository.findById(usuarioLogado.usuarioId()));
        foto.setMomento(dto.momento());
        foto.setLegenda(texto(dto.legenda()));
        foto.setTipoConteudo(imagem.tipo());
        foto.setTamanhoBytes(imagem.bytes().length);
        foto.setConteudo(imagem.bytes());
        fotoRepository.persistAndFlush(foto);

        auditoria.registrar(tenantId, Auditoria.Acao.CRIOU, Auditoria.Entidade.FOTO, foto.getId(), clienteId,
                "Adicionou foto (" + momento(dto.momento()) + ") de " + cliente.getNome());
        return paraResponse(foto);
    }

    @Transactional
    public void removerFoto(UUID tenantId, UUID clienteId, UUID fotoId) {
        clienteService.exigirEquipeViva();
        FotoCliente foto = exigirFoto(tenantId, clienteId, fotoId);
        fotoRepository.softDelete(foto);
        auditoria.registrar(tenantId, Auditoria.Acao.REMOVEU, Auditoria.Entidade.FOTO, fotoId, clienteId,
                "Apagou foto (" + momento(foto.getMomento()) + ") de " + foto.getCliente().getNome());
    }

    /** Quem faz aniversario no mes, em ordem de dia. Mes nulo: o mes de hoje no estudio. */
    @Transactional
    public List<AniversarianteResponse> aniversariantes(UUID tenantId, Integer mes) {
        clienteService.exigirEquipeViva();
        int alvo = mes != null ? mes : LocalDate.now(fuso(tenantId)).getMonthValue();
        if (alvo < 1 || alvo > 12) {
            throw new RegraNegocioException("MES_INVALIDO", "Mes deve ser de 1 a 12");
        }
        return fichaRepository.aniversariantes(tenantId, alvo).stream()
                .sorted(Comparator.comparingInt(f -> f.getDataNascimento().getDayOfMonth()))
                .map(f -> new AniversarianteResponse(f.getCliente().getId(), f.getCliente().getNome(),
                        f.getCliente().getTelefone(), f.getDataNascimento()))
                .toList();
    }

    /**
     * Clientes que passaram do proprio ritmo sem voltar e sem nada marcado. Quem
     * volta a cada 20 dias some aos 30; quem veio uma vez so, aos 60. Os mais
     * atrasados em relacao ao ritmo deles vem primeiro.
     */
    @Transactional
    public List<ClienteSumidoResponse> sumidos(UUID tenantId) {
        clienteService.exigirEquipeViva();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<Object[]> linhas = agendamentoRepository.ritmoDeVisitas(tenantId);
        Set<UUID> comHorarioMarcado = agendamentoRepository.clientesComHorarioFuturo(tenantId, agora);

        record Candidato(ClienteSumidoResponse resposta, double atraso) {
        }
        List<Candidato> candidatos = new ArrayList<>();
        for (Object[] l : linhas) {
            UUID id = (UUID) l[0];
            if (comHorarioMarcado.contains(id)) {
                continue;
            }
            long visitas = (Long) l[1];
            OffsetDateTime primeira = (OffsetDateTime) l[2];
            OffsetDateTime ultima = (OffsetDateTime) l[3];
            Integer intervalo = visitas >= 2
                    ? (int) Math.round((double) Duration.between(primeira, ultima).toDays() / (visitas - 1))
                    : null;
            int limite = intervalo == null ? DIAS_SUMIDO_SEM_HISTORICO
                    : Math.max(DIAS_SUMIDO_MINIMO, (int) Math.round(intervalo * 1.5));
            int dias = (int) Duration.between(ultima, agora).toDays();
            if (dias <= limite) {
                continue;
            }
            Usuario u = (Usuario) l[4];
            candidatos.add(new Candidato(new ClienteSumidoResponse(id, u.getNome(), u.getTelefone(), visitas,
                    ultima, dias, intervalo), (double) dias / limite));
        }
        return candidatos.stream()
                .sorted(Comparator.comparingDouble(Candidato::atraso).reversed())
                .limit(50)
                .map(Candidato::resposta)
                .toList();
    }

    private FotoCliente exigirFoto(UUID tenantId, UUID clienteId, UUID fotoId) {
        return fotoRepository.buscarVivo(tenantId, fotoId)
                .filter(f -> f.getCliente().getId().equals(clienteId))
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Foto", fotoId));
    }

    private Agendamento resolverAgendamento(UUID tenantId, UUID clienteId, UUID agendamentoId) {
        if (agendamentoId == null) {
            return null;
        }
        return agendamentoRepository.buscarVivo(tenantId, agendamentoId)
                .filter(a -> a.getCliente().getId().equals(clienteId))
                .orElseThrow(() -> new RegraNegocioException("AGENDAMENTO_INVALIDO",
                        "O atendimento informado nao e deste cliente"));
    }

    private ZoneId fuso(UUID tenantId) {
        return tenantService.porId(tenantId).zoneId();
    }

    private static FotoResponse paraResponse(FotoCliente f) {
        return new FotoResponse(f.getId(), f.getMomento(), f.getLegenda(),
                f.getAgendamento() != null ? f.getAgendamento().getId() : null, f.getTipoConteudo(),
                f.getTamanhoBytes(), f.getAutor() != null ? f.getAutor().getNome() : null, f.getCreatedAt());
    }

    private static String momento(FotoCliente.Momento m) {
        return switch (m) {
            case ANTES -> "antes";
            case DEPOIS -> "depois";
            case OUTRA -> "outra";
        };
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Sem repetidas, sem vazias, na ordem em que vieram. */
    private static List<String> etiquetas(List<String> brutas) {
        if (brutas == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> limpas = new LinkedHashSet<>();
        for (String e : brutas) {
            String t = texto(e);
            if (t != null) {
                limpas.add(t);
            }
        }
        return new ArrayList<>(limpas);
    }
}
