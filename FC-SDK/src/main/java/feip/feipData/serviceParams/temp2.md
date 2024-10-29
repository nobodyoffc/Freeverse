    public static void updateParamsPricePerKBytes(BufferedReader br, Params params) throws IOException {
        String str;
        System.out.println("\nThe price per request of your service: "+ params.getPricePerKBytes());
        System.out.println("Input the price per request of your service if you want to change it . Enter to keep it:");
        float flo = 0;
        while(true) {
            str = br.readLine();
            if(!"".equals(str)) {
                try {
                    flo = Float.parseFloat(str);
                    params.setPricePerKBytes(String.valueOf(flo));
                    break;
                }catch(NumberFormatException e) {
                    System.out.println("It isn't a number. Input again:");
                }
            }else break;
        }
    }