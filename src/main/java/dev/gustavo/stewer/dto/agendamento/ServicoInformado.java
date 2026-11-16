package dev.gustavo.stewer.dto.agendamento;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * O pedido de agendamento traz pelo menos um servico, em {@code servicoId} ou
 * em {@code servicoIds}. A violacao sai no campo {@code servicoId}, o mesmo de
 * antes de existir a lista: o app ja sabe mostrar erro nele.
 */
@Documented
@Constraint(validatedBy = ServicoInformado.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServicoInformado {

    String message() default "servicoId e obrigatorio";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<ServicoInformado, AgendamentoRequest> {

        @Override
        public boolean isValid(AgendamentoRequest pedido, ConstraintValidatorContext contexto) {
            if (pedido == null || !pedido.servicosPedidos().isEmpty()) {
                return true;
            }
            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("servicoId")
                    .addConstraintViolation();
            return false;
        }
    }
}
