package dev.gustavo.stewer.service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.gustavo.stewer.domain.Estudio;
import dev.gustavo.stewer.domain.ImagemPublica;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.dto.estudio.EstudioRequest;
import dev.gustavo.stewer.dto.estudio.EstudioResponse;
import dev.gustavo.stewer.dto.estudio.ImagemRequest;
import dev.gustavo.stewer.dto.estudio.ImagemResponse;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.repository.EstudioRepository;
import dev.gustavo.stewer.repository.ImagemPublicaRepository;
import dev.gustavo.stewer.repository.ServicoRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * O estudio editavel pelo admin: dados de contato e localizacao, retrato, fotos do
 * trabalho e fotos de exemplo de cada servico. Tudo o que muda entra na auditoria.
 */
@ApplicationScoped
public class EstudioService {

    static final int FOTO_MAXIMA = 1_500_000;
    static final int FOTOS_DO_TRABALHO = 24;
    static final int FOTOS_POR_SERVICO = 6;

    /** "@-10.19,-48.32", "q=-10.19,-48.32", "query=-10.19%2C-48.32" ou "!3d-10.19!4d-48.32". */
    private static final Pattern COORDENADAS = Pattern.compile(
            "(?:@|[?&](?:q|query|ll)=)(-?\\d{1,2}\\.\\d+)(?:,|%2C)\\s*(-?\\d{1,3}\\.\\d+)");
    private static final Pattern COORDENADAS_PIN = Pattern.compile("!3d(-?\\d{1,2}\\.\\d+)!4d(-?\\d{1,3}\\.\\d+)");

    @Inject
    EstudioRepository estudioRepository;

    @Inject
    ImagemPublicaRepository imagemRepository;

    @Inject
    ServicoRepository servicoRepository;

    @Inject
    Auditoria auditoria;

    public EstudioResponse ver(UUID tenantId) {
        Estudio e = estudioRepository.doTenant(tenantId).orElse(null);
        List<ImagemPublica> retratos = imagemRepository.doTipo(tenantId, ImagemPublica.Tipo.RETRATO);
        List<ImagemResponse> fotos = imagemRepository.doTipo(tenantId, ImagemPublica.Tipo.TRABALHO).stream()
                .map(EstudioService::paraResponse).toList();
        ImagemResponse retrato = retratos.isEmpty() ? null : paraResponse(retratos.get(retratos.size() - 1));
        if (e == null) {
            return new EstudioResponse(null, null, null, null, null, null, null, null, null, null, null, null,
                    retrato, fotos, null);
        }
        return new EstudioResponse(e.getNomeLocal(), e.getDescricao(), e.getEndereco(), e.getRegiao(),
                e.getHorario(), e.getLatitude(), e.getLongitude(), e.getLinkMapa(), e.getWhatsapp(),
                e.getInstagram(), e.getSite(), e.getFrase(), retrato, fotos, e.getUpdatedAt());
    }

    @Transactional
    public EstudioResponse salvar(UUID tenantId, EstudioRequest dto) {
        Estudio e = estudioRepository.doTenant(tenantId).orElseGet(() -> {
            Estudio novo = new Estudio();
            novo.setTenantId(tenantId);
            return novo;
        });
        e.setNomeLocal(texto(dto.nomeLocal()));
        e.setDescricao(texto(dto.descricao()));
        e.setEndereco(texto(dto.endereco()));
        e.setRegiao(texto(dto.regiao()));
        e.setHorario(texto(dto.horario()));
        e.setLinkMapa(texto(dto.linkMapa()));
        e.setWhatsapp(soDigitos(dto.whatsapp()));
        e.setInstagram(instagram(dto.instagram()));
        e.setSite(texto(dto.site()));
        e.setFrase(texto(dto.frase()));

        Double lat = dto.latitude();
        Double lon = dto.longitude();
        if ((lat == null || lon == null) && e.getLinkMapa() != null) {
            double[] doLink = coordenadasDoLink(e.getLinkMapa());
            if (doLink != null) {
                lat = doLink[0];
                lon = doLink[1];
            }
        }
        if ((lat == null) != (lon == null)) {
            throw new RegraNegocioException("COORDENADAS_INCOMPLETAS", "Informe latitude e longitude juntas");
        }
        e.setLatitude(lat);
        e.setLongitude(lon);
        estudioRepository.persistAndFlush(e);

        auditoria.registrar(tenantId, Auditoria.Acao.ALTEROU, Auditoria.Entidade.ESTUDIO, e.getId(), null,
                "Atualizou os dados do estúdio");
        return ver(tenantId);
    }

    /** Retrato troca o anterior; foto do trabalho entra no fim da lista. */
    @Transactional
    public ImagemResponse adicionarFoto(UUID tenantId, ImagemRequest dto) {
        ImagemPublica.Tipo tipo = "RETRATO".equalsIgnoreCase(dto.tipo())
                ? ImagemPublica.Tipo.RETRATO : ImagemPublica.Tipo.TRABALHO;
        if (tipo == ImagemPublica.Tipo.RETRATO) {
            imagemRepository.doTipo(tenantId, tipo).forEach(imagemRepository::softDelete);
        } else if (imagemRepository.doTipo(tenantId, tipo).size() >= FOTOS_DO_TRABALHO) {
            throw new RegraNegocioException("FOTOS_DEMAIS",
                    "Já são " + FOTOS_DO_TRABALHO + " fotos do trabalho. Apague uma antes de pôr outra");
        }
        ImagemPublica imagem = nova(tenantId, tipo, null, dto);
        auditoria.registrar(tenantId, Auditoria.Acao.CRIOU, Auditoria.Entidade.ESTUDIO, imagem.getId(), null,
                tipo == ImagemPublica.Tipo.RETRATO ? "Trocou o retrato do estúdio" : "Adicionou uma foto do trabalho");
        return paraResponse(imagem);
    }

    @Transactional
    public void removerFoto(UUID tenantId, UUID fotoId) {
        ImagemPublica imagem = imagemRepository.buscarVivo(tenantId, fotoId)
                .filter(i -> i.getTipo() != ImagemPublica.Tipo.SERVICO)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Foto", fotoId));
        imagemRepository.softDelete(imagem);
        auditoria.registrar(tenantId, Auditoria.Acao.REMOVEU, Auditoria.Entidade.ESTUDIO, fotoId, null,
                imagem.getTipo() == ImagemPublica.Tipo.RETRATO
                        ? "Tirou o retrato do estúdio" : "Apagou uma foto do trabalho");
    }

    /** Leva a foto para a posicao pedida (0 = primeira) e renumera as outras. */
    @Transactional
    public List<ImagemResponse> moverFoto(UUID tenantId, UUID fotoId, int posicao) {
        List<ImagemPublica> fotos = new java.util.ArrayList<>(
                imagemRepository.doTipo(tenantId, ImagemPublica.Tipo.TRABALHO));
        ImagemPublica alvo = fotos.stream().filter(f -> f.getId().equals(fotoId)).findFirst()
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Foto", fotoId));
        fotos.remove(alvo);
        fotos.add(Math.max(0, Math.min(posicao, fotos.size())), alvo);
        for (int i = 0; i < fotos.size(); i++) {
            fotos.get(i).setOrdem(i);
        }
        imagemRepository.flush();
        return fotos.stream().map(EstudioService::paraResponse).toList();
    }

    @Transactional
    public ImagemResponse adicionarFotoDoServico(UUID tenantId, UUID servicoId, ImagemRequest dto) {
        Servico servico = servicoRepository.buscarVivo(tenantId, servicoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Servico", servicoId));
        if (imagemRepository.doServico(tenantId, servicoId).size() >= FOTOS_POR_SERVICO) {
            throw new RegraNegocioException("FOTOS_DEMAIS",
                    "Cada serviço tem até " + FOTOS_POR_SERVICO + " fotos de exemplo");
        }
        ImagemPublica imagem = nova(tenantId, ImagemPublica.Tipo.SERVICO, servico, dto);
        auditoria.registrar(tenantId, Auditoria.Acao.CRIOU, Auditoria.Entidade.SERVICO, servicoId, null,
                "Adicionou uma foto de exemplo em " + servico.getNome());
        return paraResponse(imagem);
    }

    @Transactional
    public void removerFotoDoServico(UUID tenantId, UUID servicoId, UUID fotoId) {
        ImagemPublica imagem = imagemRepository.buscarVivo(tenantId, fotoId)
                .filter(i -> i.getServico() != null && i.getServico().getId().equals(servicoId))
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Foto", fotoId));
        imagemRepository.softDelete(imagem);
        auditoria.registrar(tenantId, Auditoria.Acao.REMOVEU, Auditoria.Entidade.SERVICO, servicoId, null,
                "Apagou uma foto de exemplo de " + imagem.getServico().getNome());
    }

    /** A foto em si, para a URL publica. */
    @Transactional
    public ImagemPublica conteudo(UUID fotoId) {
        ImagemPublica imagem = imagemRepository.publica(fotoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Foto", fotoId));
        imagem.getConteudo(); // carrega o lazy dentro da transacao
        return imagem;
    }

    private ImagemPublica nova(UUID tenantId, ImagemPublica.Tipo tipo, Servico servico, ImagemRequest dto) {
        ImagemRecebida.Imagem recebida = ImagemRecebida.decodificar(dto.tipoConteudo(), dto.conteudoBase64(),
                FOTO_MAXIMA, "1,5 MB");
        ImagemPublica imagem = new ImagemPublica();
        imagem.setTenantId(tenantId);
        imagem.setTipo(tipo);
        imagem.setServico(servico);
        imagem.setOrdem(imagemRepository.proximaOrdem(tenantId, tipo, servico == null ? null : servico.getId()));
        imagem.setLegenda(texto(dto.legenda()));
        imagem.setTipoConteudo(recebida.tipo());
        imagem.setTamanhoBytes(recebida.bytes().length);
        imagem.setConteudo(recebida.bytes());
        imagemRepository.persistAndFlush(imagem);
        return imagem;
    }

    static ImagemResponse paraResponse(ImagemPublica i) {
        return new ImagemResponse(i.getId(), i.getLegenda(), i.getOrdem());
    }

    static double[] coordenadasDoLink(String link) {
        for (Pattern p : List.of(COORDENADAS_PIN, COORDENADAS)) {
            Matcher m = p.matcher(link);
            if (m.find()) {
                double lat = Double.parseDouble(m.group(1));
                double lon = Double.parseDouble(m.group(2));
                if (Math.abs(lat) <= 90 && Math.abs(lon) <= 180) {
                    return new double[] { lat, lon };
                }
            }
        }
        return null;
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String soDigitos(String s) {
        String d = s == null ? "" : s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    /** "@stewer", "instagram.com/stewer/" e "stewer" viram "stewer". */
    static String instagram(String s) {
        String t = texto(s);
        if (t == null) {
            return null;
        }
        t = t.replaceFirst("(?i)^https?://(www\\.)?instagram\\.com/", "").replaceAll("[/?].*$", "")
                .replaceFirst("^@", "");
        return t.isBlank() ? null : t;
    }
}
