package javaTools.http;

public enum HttpRequestMethod {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    PATCH("PATCH"),
    HEAD("HEAD"),
    OPTIONS("OPTIONS"),
    TRACE("TRACE");

    private final String method;

    HttpRequestMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    @Override
    public String toString() {
        return method;
    }

    public static void main(String[] args) {
        for (HttpRequestMethod method : HttpRequestMethod.values()) {
            System.out.println("HTTP Method: " + method.getMethod());
        }
    }
}

