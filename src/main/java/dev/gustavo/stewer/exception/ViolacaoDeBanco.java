package dev.gustavo.stewer.exception;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Traduz violacao de restricao do PostgreSQL em conflito de negocio.
 *
 * <p>Os services checam "ja existe?" antes de inserir, o que resolve o caso
 * comum sem ir ao banco. Mas entre a checagem e o insert cabe a transacao do
 * vizinho: em READ COMMITTED nenhuma das duas enxerga a outra, e as duas
 * tentam. Quem segura de verdade e a restricao -- e sem esta traducao ela subia
 * crua, virava 500 ERRO_INTERNO e enchia o log de stack trace para algo que e
 * so "alguem chegou antes".
 *
 * <p>A deteccao olha o SQLSTATE, e nao o texto da mensagem, que muda com a
 * versao e o idioma do servidor. O nome da restricao entra como segunda pista,
 * para nao confundir duas restricoes diferentes na mesma tabela.
 */
public final class ViolacaoDeBanco {

    /** PostgreSQL: unique_violation. */
    private static final String UNIQUE_VIOLATION = "23505";

    /** PostgreSQL: exclusion_violation. */
    private static final String EXCLUSION_VIOLATION = "23P01";

    /**
     * As restricoes que representam uma regra de negocio conhecida, com o
     * conflito que cada uma deve virar.
     *
     * <p>A ordem nao importa: os nomes sao distintos. O que importa e a lista
     * ser exaustiva -- uma restricao fora dela continua virando 500, que e o
     * certo para um defeito que ninguem previu.
     */
    private static final List<Conflito> CONHECIDAS = List.of(
            new Conflito("ux_agendamento_sem_sobreposicao", EXCLUSION_VIOLATION,
                    "HORARIO_OCUPADO", "Ja existe um agendamento neste horario"),
            new Conflito("ux_usuario_tenant_email", UNIQUE_VIOLATION,
                    "EMAIL_EM_USO", "Ja existe uma conta com este e-mail"),
            new Conflito("ux_servico_tenant_nome", UNIQUE_VIOLATION,
                    "NOME_EM_USO", "Ja existe um servico com este nome"),
            new Conflito("ux_profissional_usuario", UNIQUE_VIOLATION,
                    "JA_E_PROFISSIONAL", "Este usuario ja tem ficha de profissional"));

    private record Conflito(String constraint, String sqlstate, String codigo, String mensagem) {
    }

    private ViolacaoDeBanco() {
    }

    /**
     * O conflito de negocio correspondente a esta falha, se houver.
     *
     * <p>Existe porque a violacao nem sempre aparece onde o service escreve: com
     * id gerado pela aplicacao, o Hibernate pode adiar o INSERT para o commit, e
     * ai a excecao ja escapou do {@code try} do service e chega embrulhada em
     * RollbackException. Resolver no mapper pega os dois momentos.
     */
    public static Optional<ConflitoException> comoConflito(Throwable erro) {
        for (Conflito conhecida : CONHECIDAS) {
            if (bate(erro, conhecida.sqlstate(), conhecida.constraint())) {
                return Optional.of(
                        new ConflitoException(conhecida.codigo(), conhecida.mensagem()));
            }
        }
        return Optional.empty();
    }

    /**
     * Percorre a cadeia de causas atras do SQLSTATE esperado.
     *
     * <p>O nome da restricao e procurado na mensagem de qualquer nivel: o
     * driver o coloca no detalhe, mas o Hibernate reembrulha a excecao mais de
     * uma vez pelo caminho.
     */
    private static boolean bate(Throwable erro, String sqlstate, String nomeDaConstraint) {
        boolean estadoConfere = false;
        boolean nomeConfere = false;
        boolean achouSqlException = false;

        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                achouSqlException = true;
                if (sqlstate.equals(sql.getSQLState())) {
                    estadoConfere = true;
                }
            }
            String mensagem = causa.getMessage();
            if (mensagem != null && mensagem.contains(nomeDaConstraint)) {
                nomeConfere = true;
            }
        }

        // O nome e obrigatorio: sem ele nao da para saber QUAL restricao caiu, e
        // tratar uma violacao desconhecida como conflito de negocio esconderia
        // um defeito atras de um 409 educado. O SQLSTATE confirma -- e so e
        // dispensado quando a SQLException nem chegou na cadeia de causas.
        return nomeConfere && (estadoConfere || !achouSqlException);
    }
}
