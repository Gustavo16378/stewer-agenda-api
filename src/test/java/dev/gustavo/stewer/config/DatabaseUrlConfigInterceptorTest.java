package dev.gustavo.stewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.gustavo.stewer.config.DatabaseUrlConfigInterceptor.Conexao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A quebra da connection string em URL, usuario e senha.
 *
 * <p>E o unico pedaco do deploy que depende de parsing, e o erro aqui e caro:
 * uma query string perdida tira o {@code sslmode=require} e o Neon recusa a
 * conexao; uma senha nao decodificada derruba a autenticacao com uma mensagem
 * que nao aponta para a causa.
 */
@DisplayName("DATABASE_URL do painel -> as tres propriedades do Quarkus")
class DatabaseUrlConfigInterceptorTest {

    @Test
    @DisplayName("string do Neon, com sslmode e sem porta")
    void stringDoNeon() {
        Conexao c = Conexao.de(
                "postgresql://neondb_owner:npg_Ab3xYz@ep-cool-sun-12345678.us-east-1.aws.neon.tech"
                        + "/neondb?sslmode=require");

        assertEquals("jdbc:postgresql://ep-cool-sun-12345678.us-east-1.aws.neon.tech"
                + "/neondb?sslmode=require", c.jdbcUrl());
        assertEquals("neondb_owner", c.usuario());
        assertEquals("npg_Ab3xYz", c.senha());
    }

    @Test
    @DisplayName("o esquema curto postgres:// tambem vale")
    void esquemaCurto() {
        Conexao c = Conexao.de("postgres://u:s@host.exemplo/banco");

        assertEquals("jdbc:postgresql://host.exemplo/banco", c.jdbcUrl());
        assertEquals("u", c.usuario());
        assertEquals("s", c.senha());
    }

    @Test
    @DisplayName("porta explicita e preservada")
    void comPorta() {
        Conexao c = Conexao.de("postgresql://u:s@localhost:5433/stewer_agenda");

        assertEquals("jdbc:postgresql://localhost:5433/stewer_agenda", c.jdbcUrl());
    }

    @Test
    @DisplayName("senha com caractere especial chega decodificada ao driver")
    void senhaPercentEncoded() {
        // "p@ss w/ord#" escrito como a URL exige
        Conexao c = Conexao.de("postgresql://usuario:p%40ss%20w%2Ford%23@host/banco");

        assertEquals("p@ss w/ord#", c.senha());
        assertEquals("usuario", c.usuario());
    }

    @Test
    @DisplayName("varios parametros na query string continuam inteiros")
    void queryStringCompleta() {
        Conexao c = Conexao.de(
                "postgresql://u:s@host/banco?sslmode=require&channel_binding=require&application_name=stewer");

        assertEquals("jdbc:postgresql://host/banco"
                + "?sslmode=require&channel_binding=require&application_name=stewer",
                c.jdbcUrl());
    }

    @Test
    @DisplayName("URL sem credenciais devolve usuario e senha vazios")
    void semCredenciais() {
        Conexao c = Conexao.de("postgresql://host/banco");

        assertEquals("jdbc:postgresql://host/banco", c.jdbcUrl());
        assertEquals("", c.usuario());
        assertEquals("", c.senha());
    }

    @Test
    @DisplayName("usuario sem senha nao quebra o parsing")
    void usuarioSemSenha() {
        Conexao c = Conexao.de("postgresql://somente_usuario@host/banco");

        assertEquals("somente_usuario", c.usuario());
        assertEquals("", c.senha());
    }

    @Test
    @DisplayName("o host do pooler do Neon passa igual: quem escolhe e quem cola")
    void hostDoPooler() {
        Conexao c = Conexao.de(
                "postgresql://u:s@ep-cool-sun-12345678-pooler.us-east-1.aws.neon.tech/neondb?sslmode=require");

        assertEquals("jdbc:postgresql://ep-cool-sun-12345678-pooler.us-east-1.aws.neon.tech"
                + "/neondb?sslmode=require", c.jdbcUrl());
    }
}
