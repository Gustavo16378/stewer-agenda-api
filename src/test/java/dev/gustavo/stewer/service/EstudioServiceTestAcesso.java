package dev.gustavo.stewer.service;

/** Abre para os testes o leitor de coordenadas, que e de pacote. */
public final class EstudioServiceTestAcesso {

    private EstudioServiceTestAcesso() {
    }

    public static double[] coordenadas(String link) {
        return EstudioService.coordenadasDoLink(link);
    }
}
