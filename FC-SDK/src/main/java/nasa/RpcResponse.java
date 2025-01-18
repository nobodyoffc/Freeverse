package nasa;


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

//    {
//        "result": [{
//        "txid": "17c9ce8f90d1848d483c5fcba0afd700851072ddbdf7bf88f7430e02743a0893",
//                "vout": 0,
//                "address": "DJgizn89UvzNAW2wYAgN4qot82H9hjmAHM",
//                "account": "",
//                "scriptPubKey": "76a9149494f686025f04afe4c837139ea849b58ff3b99988ac",
//                "amount": 10.00000000,
//                "confirmations": 0,
//                "spendable": true,
//                "solvable": true
//    }],
//        "error": null,
//            "id": "1"
//    }

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
