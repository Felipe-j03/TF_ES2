package br.pucrs.es2.acompanhamento.moodle;

public class MoodleException extends RuntimeException {

    private final String errorCode;

    public MoodleException(String errorCode, String message) {
        super("Moodle retornou erro [" + errorCode + "]: " + message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
