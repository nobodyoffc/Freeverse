package javaTools.http;

public enum RequestMethod {
    GET("GET"),
    POST("POST"),
    TCP("TCP"),
    PUT("PUT"),
    DELETE("DELETE"),
    PATCH("PATCH"),
    HEAD("HEAD"),
    OPTIONS("OPTIONS"),
    TRACE("TRACE");

    private final String method;

    RequestMethod(String method) {
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
        for (RequestMethod method : RequestMethod.values()) {
            System.out.println("HTTP Method: " + method.getMethod());
        }
    }
}

