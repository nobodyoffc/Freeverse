package fudp.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProofOfWork.
 */
class ProofOfWorkTest {

    @Test
    void testGenerateNonce() {
        byte[] nonce1 = ProofOfWork.generateNonce();
        byte[] nonce2 = ProofOfWork.generateNonce();
        
        assertNotNull(nonce1);
        assertNotNull(nonce2);
        assertEquals(16, nonce1.length);
        assertEquals(16, nonce2.length);
        
        // Nonces should be different (with extremely high probability)
        assertFalse(java.util.Arrays.equals(nonce1, nonce2));
    }

    @Test
    void testVerifyInvalidInputs() {
        byte[] validNonce = ProofOfWork.generateNonce();
        byte[] validSolution = new byte[8];
        
        // Null nonce
        assertFalse(ProofOfWork.verify(null, validSolution, 8));
        
        // Wrong nonce length
        assertFalse(ProofOfWork.verify(new byte[15], validSolution, 8));
        assertFalse(ProofOfWork.verify(new byte[17], validSolution, 8));
        
        // Null solution
        assertFalse(ProofOfWork.verify(validNonce, null, 8));
        
        // Wrong solution length
        assertFalse(ProofOfWork.verify(validNonce, new byte[7], 8));
        assertFalse(ProofOfWork.verify(validNonce, new byte[9], 8));
        
        // Invalid difficulty
        assertFalse(ProofOfWork.verify(validNonce, validSolution, 3));  // Below MIN
        assertFalse(ProofOfWork.verify(validNonce, validSolution, 25)); // Above MAX
    }

    @Test
    @Timeout(5)
    void testSolveAndVerifyDifficulty8() throws TimeoutException {
        byte[] nonce = ProofOfWork.generateNonce();
        int difficulty = 8;
        
        byte[] solution = ProofOfWork.solve(nonce, difficulty, 5000);
        
        assertNotNull(solution);
        assertEquals(8, solution.length);
        assertTrue(ProofOfWork.verify(nonce, solution, difficulty));
    }

    @Test
    @Timeout(10)
    void testSolveAndVerifyDifficulty12() throws TimeoutException {
        byte[] nonce = ProofOfWork.generateNonce();
        int difficulty = 12;
        
        byte[] solution = ProofOfWork.solve(nonce, difficulty, 10000);
        
        assertNotNull(solution);
        assertEquals(8, solution.length);
        assertTrue(ProofOfWork.verify(nonce, solution, difficulty));
    }

    @Test
    void testSolveTimeout() {
        byte[] nonce = ProofOfWork.generateNonce();
        // Difficulty 24 requires ~16M attempts, should timeout in 1ms
        int difficulty = 24;
        
        assertThrows(TimeoutException.class, () -> {
            ProofOfWork.solve(nonce, difficulty, 1);
        });
    }

    @Test
    void testSolveInvalidInputs() {
        byte[] validNonce = ProofOfWork.generateNonce();
        
        // Null nonce
        assertThrows(IllegalArgumentException.class, () -> {
            ProofOfWork.solve(null, 8, 1000);
        });
        
        // Wrong nonce length
        assertThrows(IllegalArgumentException.class, () -> {
            ProofOfWork.solve(new byte[15], 8, 1000);
        });
        
        // Invalid difficulty
        assertThrows(IllegalArgumentException.class, () -> {
            ProofOfWork.solve(validNonce, 3, 1000);
        });
        
        // Invalid timeout
        assertThrows(IllegalArgumentException.class, () -> {
            ProofOfWork.solve(validNonce, 8, 0);
        });
    }

    @Test
    void testCountLeadingZeroBits() {
        // All zeros
        assertEquals(256, ProofOfWork.countLeadingZeroBits(new byte[32]));
        
        // First byte has value 1 (7 leading zeros in first byte)
        byte[] hash1 = new byte[32];
        hash1[0] = 0x01;
        assertEquals(7, ProofOfWork.countLeadingZeroBits(hash1));
        
        // First byte has value 0x80 (1 leading zero in first byte)
        byte[] hash2 = new byte[32];
        hash2[0] = (byte) 0x80;
        assertEquals(0, ProofOfWork.countLeadingZeroBits(hash2));
        
        // First byte zero, second byte has value 1 (15 leading zeros)
        byte[] hash3 = new byte[32];
        hash3[1] = 0x01;
        assertEquals(15, ProofOfWork.countLeadingZeroBits(hash3));
        
        // First two bytes zero, third byte has value 0x0F (20 leading zeros)
        byte[] hash4 = new byte[32];
        hash4[2] = 0x0F;
        assertEquals(20, ProofOfWork.countLeadingZeroBits(hash4));
    }

    @Test
    void testEstimateSolveTime() {
        // Basic sanity checks - estimates are relative, not absolute
        long est8 = ProofOfWork.estimateSolveTimeMs(8);
        long est12 = ProofOfWork.estimateSolveTimeMs(12);
        long est16 = ProofOfWork.estimateSolveTimeMs(16);
        long est20 = ProofOfWork.estimateSolveTimeMs(20);
        
        // Should return positive values
        assertTrue(est8 >= 0, "Estimate for difficulty 8 should be non-negative");
        assertTrue(est12 >= 0, "Estimate for difficulty 12 should be non-negative");
        
        // Higher difficulty should take longer (or equal for low difficulties)
        assertTrue(est12 >= est8, "Difficulty 12 should take >= difficulty 8");
        assertTrue(est16 >= est12, "Difficulty 16 should take >= difficulty 12");
        assertTrue(est20 >= est16, "Difficulty 20 should take >= difficulty 16");
        
        // At least difficulty 20 should have noticeable estimate
        assertTrue(est20 >= 1, "Difficulty 20 should take at least 1ms");
    }

    @Test
    void testBytesToLong() {
        byte[] bytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 1};
        assertEquals(1L, ProofOfWork.bytesToLong(bytes));
        
        byte[] bytes2 = new byte[]{0, 0, 0, 0, 0, 0, 1, 0};
        assertEquals(256L, ProofOfWork.bytesToLong(bytes2));
        
        byte[] bytes3 = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 
                                    (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertEquals(-1L, ProofOfWork.bytesToLong(bytes3));
    }

    @Test
    @Timeout(5)
    void testSolveInterruptible() throws Exception {
        byte[] nonce = ProofOfWork.generateNonce();
        int difficulty = 8;
        
        byte[] solution = ProofOfWork.solveInterruptible(nonce, difficulty, 5000, 1000);
        
        assertNotNull(solution);
        assertTrue(ProofOfWork.verify(nonce, solution, difficulty));
    }

    @Test
    void testSolveInterruptibleTimeout() {
        byte[] nonce = ProofOfWork.generateNonce();
        // High difficulty should timeout
        int difficulty = 24;
        
        assertThrows(TimeoutException.class, () -> {
            ProofOfWork.solveInterruptible(nonce, difficulty, 10, 100);
        });
    }

    @Test
    void testDifferentNoncesDifferentSolutions() throws TimeoutException {
        byte[] nonce1 = ProofOfWork.generateNonce();
        byte[] nonce2 = ProofOfWork.generateNonce();
        int difficulty = 8;
        
        byte[] solution1 = ProofOfWork.solve(nonce1, difficulty, 5000);
        byte[] solution2 = ProofOfWork.solve(nonce2, difficulty, 5000);
        
        // Solutions should verify only with their own nonce
        assertTrue(ProofOfWork.verify(nonce1, solution1, difficulty));
        assertTrue(ProofOfWork.verify(nonce2, solution2, difficulty));
        
        // Cross-verification should fail (with very high probability)
        // Note: There's a tiny chance this could pass if by coincidence
        // the solutions work for both nonces, but it's astronomically unlikely
        assertFalse(ProofOfWork.verify(nonce1, solution2, difficulty));
        assertFalse(ProofOfWork.verify(nonce2, solution1, difficulty));
    }
}
