package clients.NaSaClient;


public class RpcResponse {

    private Object result;
    private Error error;
    private String id;

    public boolean isBadResult(String task) {
        if (error != null) {
            System.out.println("Failed to " + task + ":" +
                    "\n\tcode:" + error.getCode()
                    + "\n\tMessage:" + error.getMessage()
                    + "\n\tRequest ID:" + id);
            return true;
        }
        return false;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    //    {
//        "result": null,
//            "error": {
//        "code": -3,
//                "message": "Expected type number, got string"
//    },
//        "id": "2"
//    }
    public static class Error {
        private int code;
        private String message;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
