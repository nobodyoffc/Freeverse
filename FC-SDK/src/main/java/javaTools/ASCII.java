package javaTools;

public class ASCII {


    // Control characters (0–31)
    public static final char NULL = 0;
    public static final char START_OF_HEADING = 1;
    public static final char START_OF_TEXT = 2;
    public static final char END_OF_TEXT = 3;
    public static final char END_OF_TRANSMISSION = 4;
    public static final char ENQUIRY = 5;
    public static final char ACKNOWLEDGE = 6;
    public static final char BELL = 7;
    public static final char BACKSPACE = 8;
    public static final char HORIZONTAL_TAB = 9;
    public static final char LINE_FEED = 10;
    public static final char VERTICAL_TAB = 11;
    public static final char FORM_FEED = 12;
    public static final char CARRIAGE_RETURN = 13;
    public static final char SHIFT_OUT = 14;
    public static final char SHIFT_IN = 15;
    public static final char DATA_LINK_ESCAPE = 16;
    public static final char DEVICE_CONTROL_1 = 17;
    public static final char DEVICE_CONTROL_2 = 18;
    public static final char DEVICE_CONTROL_3 = 19;
    public static final char DEVICE_CONTROL_4 = 20;
    public static final char NEGATIVE_ACKNOWLEDGE = 21;
    public static final char SYNCHRONOUS_IDLE = 22;
    public static final char END_OF_TRANSMISSION_BLOCK = 23;
    public static final char CANCEL = 24;
    public static final char END_OF_MEDIUM = 25;
    public static final char SUBSTITUTE = 26;
    public static final char ESCAPE = 27;
    public static final char FILE_SEPARATOR = 28;
    public static final char GROUP_SEPARATOR = 29;
    public static final char RECORD_SEPARATOR = 30;
    public static final char UNIT_SEPARATOR = 31;

    // Printable characters (32–127)
    public static final char SPACE = 32;
    public static final char EXCLAMATION_MARK = 33;
    public static final char DOUBLE_QUOTE = 34;
    public static final char HASH = 35;
    public static final char DOLLAR = 36;
    public static final char PERCENT = 37;
    public static final char AMPERSAND = 38;
    public static final char SINGLE_QUOTE = 39;
    public static final char LEFT_PARENTHESIS = 40;
    public static final char RIGHT_PARENTHESIS = 41;
    public static final char ASTERISK = 42;
    public static final char PLUS = 43;
    public static final char COMMA = 44;
    public static final char HYPHEN = 45;
    public static final char PERIOD = 46;
    public static final char SLASH = 47;
    public static final char ZERO = 48;
    public static final char ONE = 49;
    public static final char TWO = 50;
    public static final char THREE = 51;
    public static final char FOUR = 52;
    public static final char FIVE = 53;
    public static final char SIX = 54;
    public static final char SEVEN = 55;
    public static final char EIGHT = 56;
    public static final char NINE = 57;
    public static final char COLON = 58;
    public static final char SEMICOLON = 59;
    public static final char LESS_THAN = 60;
    public static final char EQUALS = 61;
    public static final char GREATER_THAN = 62;
    public static final char QUESTION_MARK = 63;
    public static final char AT = 64;
    public static final char A = 65;
    public static final char B = 66;
    public static final char C = 67;
    public static final char D = 68;
    public static final char E = 69;
    public static final char F = 70;
    public static final char G = 71;
    public static final char H = 72;
    public static final char I = 73;
    public static final char J = 74;
    public static final char K = 75;
    public static final char L = 76;
    public static final char M = 77;
    public static final char N = 78;
    public static final char O = 79;
    public static final char P = 80;
    public static final char Q = 81;
    public static final char R = 82;
    public static final char S = 83;
    public static final char T = 84;
    public static final char U = 85;
    public static final char V = 86;
    public static final char W = 87;
    public static final char X = 88;
    public static final char Y = 89;
    public static final char Z = 90;
    public static final char LEFT_BRACKET = 91;
    public static final char BACKSLASH = 92;
    public static final char RIGHT_BRACKET = 93;
    public static final char CARET = 94;
    public static final char UNDERSCORE = 95;
    public static final char GRAVE_ACCENT = 96;
    public static final char a = 97;
    public static final char b = 98;
    public static final char c = 99;
    public static final char d = 100;
    public static final char e = 101;
    public static final char f = 102;
    public static final char g = 103;
    public static final char h = 104;
    public static final char i = 105;
    public static final char j = 106;
    public static final char k = 107;
    public static final char l = 108;
    public static final char m = 109;
    public static final char n = 110;
    public static final char o = 111;
    public static final char p = 112;
    public static final char q = 113;
    public static final char r = 114;
    public static final char s = 115;
    public static final char t = 116;
    public static final char u = 117;
    public static final char v = 118;
    public static final char w = 119;
    public static final char x = 120;
    public static final char y = 121;
    public static final char z = 122;
    public static final char LEFT_CURLY_BRACE = 123;
    public static final char VERTICAL_BAR = 124;
    public static final char RIGHT_CURLY_BRACE = 125;
    public static final char TILDE = 126;
    public static final char DELETE = 127;

    public enum AsciiChar {
        // Control characters
        NULL(0), START_OF_HEADING(1), START_OF_TEXT(2), END_OF_TEXT(3),
        END_OF_TRANSMISSION(4), ENQUIRY(5), ACKNOWLEDGE(6), BELL(7),
        BACKSPACE(8), HORIZONTAL_TAB(9), LINE_FEED(10), VERTICAL_TAB(11),
        FORM_FEED(12), CARRIAGE_RETURN(13), SHIFT_OUT(14), SHIFT_IN(15),
        DATA_LINK_ESCAPE(16), DEVICE_CONTROL_1(17), DEVICE_CONTROL_2(18),
        DEVICE_CONTROL_3(19), DEVICE_CONTROL_4(20), NEGATIVE_ACKNOWLEDGE(21),
        SYNCHRONOUS_IDLE(22), END_OF_TRANSMISSION_BLOCK(23), CANCEL(24),
        END_OF_MEDIUM(25), SUBSTITUTE(26), ESCAPE(27), FILE_SEPARATOR(28),
        GROUP_SEPARATOR(29), RECORD_SEPARATOR(30), UNIT_SEPARATOR(31),

        // Printable characters
        SPACE(32), EXCLAMATION_MARK(33), DOUBLE_QUOTE(34), HASH(35), DOLLAR(36),
        PERCENT(37), AMPERSAND(38), SINGLE_QUOTE(39), LEFT_PARENTHESIS(40),
        RIGHT_PARENTHESIS(41), ASTERISK(42), PLUS(43), COMMA(44), HYPHEN(45),
        PERIOD(46), SLASH(47),
        ZERO(48), ONE(49), TWO(50), THREE(51), FOUR(52), FIVE(53), SIX(54),
        SEVEN(55), EIGHT(56), NINE(57),
        COLON(58), SEMICOLON(59), LESS_THAN(60), EQUALS(61), GREATER_THAN(62),
        QUESTION_MARK(63), AT(64),
        A(65), B(66), C(67), D(68), E(69), F(70), G(71), H(72), I(73), J(74),
        K(75), L(76), M(77), N(78), O(79), P(80), Q(81), R(82), S(83), T(84),
        U(85), V(86), W(87), X(88), Y(89), Z(90),
        LEFT_BRACKET(91), BACKSLASH(92), RIGHT_BRACKET(93), CARET(94),
        UNDERSCORE(95), GRAVE_ACCENT(96),
        a(97), b(98), c(99), d(100), e(101), f(102), g(103), h(104), i(105),
        j(106), k(107), l(108), m(109), n(110), o(111), p(112), q(113),
        r(114), s(115), t(116), u(117), v(118), w(119), x(120), y(121), z(122),
        LEFT_CURLY_BRACE(123), VERTICAL_BAR(124), RIGHT_CURLY_BRACE(125),
        TILDE(126), DELETE(127);

        private final int code;

        AsciiChar(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static AsciiChar fromCode(int code) {
            for (AsciiChar asciiChar : AsciiChar.values()) {
                if (asciiChar.getCode() == code) {
                    return asciiChar;
                }
            }
            throw new IllegalArgumentException("Invalid ASCII code: " + code);
        }

        @Override
        public String toString() {
            return this.name() + "(" + code + ")";
        }

        public static void main(String[] args) {
            // Example usage
            System.out.println(AsciiChar.A.getCode()); // Outputs 65
            System.out.println(AsciiChar.fromCode(65)); // Outputs A(65)
            System.out.println(AsciiChar.fromCode(32)); // Outputs SPACE(32)
        }
    }
}
