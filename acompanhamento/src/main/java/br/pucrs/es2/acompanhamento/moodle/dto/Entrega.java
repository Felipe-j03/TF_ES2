package br.pucrs.es2.acompanhamento.moodle.dto;

public record Entrega(long tarefaId, long alunoId, String status) {

    public boolean enviada() {
        return "submitted".equals(status);
    }
}
