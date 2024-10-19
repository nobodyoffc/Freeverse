package crypto;
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
        String[] parts = bech32Address.split("1");
        byte[] data = bech32Decode(parts[1]);
        return Arrays.copyOfRange(data, 1, data.length);
    }

    public static String hash160ToBech32(byte[] hash160) {
        byte[] data = new byte[hash160.length + 1];
        data[0] = 0x00; // Witness version
        System.arraycopy(hash160, 0, data, 1, hash160.length);
        return "bc1" + bech32Encode(data);
    }

    private static String bech32Encode(byte[] data) {
        int[] values = convertBits(data, 8, 5, true);
        StringBuilder result = new StringBuilder();
        for (int v : values) {
            result.append(CHARSET.charAt(v));
        }
        return result.toString();
    }

    private static byte[] bech32Decode(String encoded) {
        int[] data = new int[encoded.length()];
        for (int i = 0; i < encoded.length(); i++) {
            data[i] = CHARSET.indexOf(encoded.charAt(i));
        }
        int[] result = convertBits(data, 5, 8, false);
        byte[] bytes = new byte[result.length];
        for (int i = 0; i < result.length; i++) {
            bytes[i] = (byte) result[i];
        }
        return bytes;
    }

    private static int[] convertBits(byte[] data, int fromBits, int toBits, boolean pad) {
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toBits) - 1;
        int[] ret = new int[data.length * fromBits / toBits + (pad ? 1 : 0)];
        int index = 0;
        for (byte value : data) {
            int b = value & 0xff;
            acc = (acc << fromBits) | b;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                ret[index++] = (acc >> bits) & maxv;
            }
        }
        if (pad && bits > 0) {
            ret[index++] = (acc << (toBits - bits)) & maxv;
        }
        return Arrays.copyOf(ret, index);
    }

    private static int[] convertBits(int[] data, int fromBits, int toBits, boolean pad) {
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toBits) - 1;
        int[] ret = new int[data.length * fromBits / toBits + (pad ? 1 : 0)];
        int index = 0;
        for (int value : data) {
            acc = (acc << fromBits) | value;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                ret[index++] = (acc >> bits) & maxv;
            }
        }
        if (pad && bits > 0) {
            ret[index++] = (acc << (toBits - bits)) & maxv;
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