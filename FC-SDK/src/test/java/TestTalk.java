import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import utils.ASCII;

public class TestTalk {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while(true){
            br.readLine();
            System.out.print("> ");

        String input = br.readLine();
        if(input.isEmpty()){
                System.out.println("Got enter.");
                continue;
            }

        int firstChar = input.charAt(0);

        switch (firstChar) {
            case ASCII.ESCAPE ->{
                System.out.println("Got ESC.");
            }
            case ASCII.DOLLAR -> {
                System.out.println("Got $.");
            }
            case ASCII.GREATER_THAN -> {
                }
            }
        }
    }
}
