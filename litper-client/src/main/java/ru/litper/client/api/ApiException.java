package ru.litper.client.api;

public class ApiException extends RuntimeException {

    private final int status;
    private final String title;
    private final String detail;

    public ApiException(int status, String title, String detail) {
        super(buildMessage(status, title, detail));
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public ApiException(int status, String title, String detail, Throwable cause) {
        super(buildMessage(status, title, detail), cause);
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public int getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    private static String buildMessage(int status, String title, String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP ").append(status);
        if (title != null && !title.isBlank()) {
            sb.append(" ").append(title);
        }
        if (detail != null && !detail.isBlank()) {
            sb.append(": ").append(detail);
        }
        return sb.toString();
    }
}
