
# There are 4 types of encrypting and decrypting:

1. SymKey: Encrypt or decrypt by a 32 bytes symmetric key.
2. Password: Encrypt or decrypt by a UTF-8 password which no longer than 64 bytes.
3. AsyOneWay: Encrypt by the public key B. A random key pair B will be generate and the new public key will be given in the encrypting result. When decrypting, only the private key B is required.
4. AsyTwoWay: Encrypt by the public key of B and the private of key A. You can decrypt it with priKeyB and pubKeyA, or, with priKeyA and pubKeyB.

# The dataType of SymKey is the base method. When encrypting or decrypting with the other 3 dataType method, a symKey will be calculated at first and then be used to encrypt or to decrypt. You can get the symKey if you need. 
