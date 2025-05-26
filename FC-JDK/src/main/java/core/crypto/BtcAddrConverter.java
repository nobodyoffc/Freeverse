package core.crypto;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class BtcAddrConverter {

    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] GENERATOR = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};

    public static String legacyToBech32(String legacyAddress) {
        byte[] decoded = Base58.decode(legacyAddress);
        byte[] hash160 = Arrays.copyOfRange(decoded, 1, 21);
        return hash160ToBech32(hash160);
    }

    public static String bech32ToLegacy(String bech32Address) {
        byte[] hash160 = bech32ToHash160(bech32Address);
        byte[] addressBytes = new byte[25];
        addressBytes[0] = 0x00; // Mainnet
        System.arraycopy(hash160, 0, addressBytes, 1, 20);
        byte[] checksum = Arrays.copyOfRange(hash256(addressBytes), 0, 4);
        System.arraycopy(checksum, 0, addressBytes, 21, 4);
        return Base58.encode(addressBytes);
    }

    public static byte[] bech32ToHash160(String bech32Address) {
        // Split human-readable part and data part
        int pos = bech32Address.lastIndexOf('1');
        String hrp = bech32Address.substring(0, pos);
        String data = bech32Address.substring(pos + 1);
        
        // Convert characters to 5-bit values
        int[] values = new int[data.length()];
        for (int i = 0; i < data.length(); i++) {
            values[i] = CHARSET.indexOf(data.charAt(i));
        }
        
        // Remove the checksum (last 6 characters)
        int[] dataValues = Arrays.copyOfRange(values, 0, values.length - 6);
        
        // Convert from 5-bit to 8-bit
        int[] converted = convertBits(dataValues, 5, 8, false);
        
        // Skip the witness version byte
        byte[] result = new byte[converted.length - 1];
        for (int i = 1; i < converted.length; i++) {
            result[i - 1] = (byte) converted[i];
        }
        
        return result;
    }

    public static String hash160ToBech32(byte[] hash160) {
        // For P2WPKH, we need exactly 20 bytes of hash160
        if (hash160.length != 20) {
            throw new IllegalArgumentException("Hash160 must be 20 bytes for P2WPKH");
        }
        
        // Witness version 0 + hash160
        int[] witnessProgram = new int[hash160.length + 1];
        witnessProgram[0] = 0; // Witness version 0
        for (int i = 0; i < hash160.length; i++) {
            witnessProgram[i + 1] = hash160[i] & 0xff;
        }
        
        // Convert to 5-bit values - this is the critical part
        int[] converted = convertBits(witnessProgram, 8, 5, true);
        
        // Create the full address with human-readable part
        return bech32Encode("bc", converted);
    }

    private static String bech32Encode(String hrp, int[] data) {
        // Calculate checksum
        int[] checksum = createChecksum(hrp, data);
        
        // Build the address
        StringBuilder sb = new StringBuilder(hrp + "1");
        
        // Add the data part
        for (int b : data) {
            sb.append(CHARSET.charAt(b));
        }
        
        // Add the checksum
        for (int b : checksum) {
            sb.append(CHARSET.charAt(b));
        }
        
        return sb.toString();
    }

    private static int[] createChecksum(String hrp, int[] data) {
        int[] values = hrpExpand(hrp);
        int[] combined = new int[values.length + data.length + 6];
        System.arraycopy(values, 0, combined, 0, values.length);
        System.arraycopy(data, 0, combined, values.length, data.length);
        
        int polymod = polymod(combined) ^ 1;
        int[] checksum = new int[6];
        for (int i = 0; i < 6; i++) {
            checksum[i] = (polymod >> 5 * (5 - i)) & 31;
        }
        return checksum;
    }
    
    private static int[] hrpExpand(String hrp) {
        int[] result = new int[hrp.length() * 2 + 1];
        for (int i = 0; i < hrp.length(); i++) {
            result[i] = hrp.charAt(i) >> 5;
            result[i + hrp.length() + 1] = hrp.charAt(i) & 31;
        }
        result[hrp.length()] = 0;
        return result;
    }
    
    private static int polymod(int[] values) {
        int chk = 1;
        for (int value : values) {
            int b = chk >> 25;
            chk = ((chk & 0x1ffffff) << 5) ^ value;
            for (int i = 0; i < 5; i++) {
                if (((b >> i) & 1) == 1) {
                    chk ^= GENERATOR[i];
                }
            }
        }
        return chk;
    }

    private static int[] convertBits(int[] data, int fromBits, int toBits, boolean pad) {
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toBits) - 1;
        // Calculate a safe maximum size for the result array
        int maxSize = (data.length * fromBits + toBits - 1) / toBits;
        int[] ret = new int[maxSize];
        int index = 0;
        
        for (int value : data) {
            if ((value >>> fromBits) != 0) {
                throw new IllegalArgumentException(
                    String.format("Invalid value: %d (exceeds %d bits)", value, fromBits));
            }
            
            acc = (acc << fromBits) | value;
            bits += fromBits;
            
            while (bits >= toBits) {
                bits -= toBits;
                ret[index++] = (acc >> bits) & maxv;
            }
        }
        
        if (pad && bits > 0) {
            ret[index++] = (acc << (toBits - bits)) & maxv;
        } else if (bits >= fromBits || ((acc << (toBits - bits)) & maxv) != 0) {
            throw new IllegalArgumentException("Could not convert bits, invalid padding");
        }
        
        return Arrays.copyOf(ret, index);
    }

    private static byte[] hash256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Couldn't find SHA-256 algorithm", e);
        }
    }

    // Base58 implementation
    private static class Base58 {
        private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

        public static String encode(byte[] input) {
            if (input.length == 0) {
                return "";
            }
            int zeros = 0;
            while (zeros < input.length && input[zeros] == 0) {
                ++zeros;
            }
            input = Arrays.copyOf(input, input.length);
            char[] encoded = new char[input.length * 2];
            int outputStart = encoded.length;
            for (int inputStart = zeros; inputStart < input.length; ) {
                encoded[--outputStart] = ALPHABET.charAt(divmod(input, inputStart, 256, 58));
                if (input[inputStart] == 0) {
                    ++inputStart;
                }
            }
            while (outputStart < encoded.length && encoded[outputStart] == ALPHABET.charAt(0)) {
                ++outputStart;
            }
            while (--zeros >= 0) {
                encoded[--outputStart] = ALPHABET.charAt(0);
            }
            return new String(encoded, outputStart, encoded.length - outputStart);
        }

        public static byte[] decode(String input) {
            if (input.length() == 0) {
                return new byte[0];
            }
            byte[] input58 = new byte[input.length()];
            for (int i = 0; i < input.length(); ++i) {
                char c = input.charAt(i);
                int digit = ALPHABET.indexOf(c);
                if (digit < 0) {
                    throw new IllegalArgumentException("Invalid character in Base58: " + c);
                }
                input58[i] = (byte) digit;
            }
            int zeros = 0;
            while (zeros < input58.length && input58[zeros] == 0) {
                ++zeros;
            }
            byte[] decoded = new byte[input.length()];
            int outputStart = decoded.length;
            for (int inputStart = zeros; inputStart < input58.length; ) {
                decoded[--outputStart] = divmod(input58, inputStart, 58, 256);
                if (input58[inputStart] == 0) {
                    ++inputStart;
                }
            }
            while (outputStart < decoded.length && decoded[outputStart] == 0) {
                ++outputStart;
            }
            return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
        }

        private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
            int remainder = 0;
            for (int i = firstDigit; i < number.length; i++) {
                int digit = (int) number[i] & 0xFF;
                int temp = remainder * base + digit;
                number[i] = (byte) (temp / divisor);
                remainder = temp % divisor;
            }
            return (byte) remainder;
        }
    }

    public static void main(String[] args) {
        String legacyAddress = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2";
        String bech32Address = legacyToBech32(legacyAddress);
        System.out.println("Legacy address: " + legacyAddress);
        System.out.println("Bech32 address: " + bech32Address);

        String convertedLegacyAddress = bech32ToLegacy(bech32Address);
        System.out.println("Converted back to Legacy: " + convertedLegacyAddress);

        byte[] hash160 = bech32ToHash160(bech32Address);
        System.out.println("Hash160 from Bech32: " + bytesToHex(hash160));

        String recreatedBech32 = hash160ToBech32(hash160);
        System.out.println("Bech32 from Hash160: " + recreatedBech32);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}