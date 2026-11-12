package dev.gustavo.stewer.dto.cliente;

import java.time.LocalDate;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Aniversariante")
public record AniversarianteResponse(UUID id, String nome, String telefone, LocalDate dataNascimento) {
}
