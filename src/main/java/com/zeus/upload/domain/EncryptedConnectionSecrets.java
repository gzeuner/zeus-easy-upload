package com.zeus.upload.domain;

public class EncryptedConnectionSecrets {

    private String iv;
    private String ciphertext;

    public EncryptedConnectionSecrets() {
    }

    public EncryptedConnectionSecrets(String iv, String ciphertext) {
        this.iv = iv;
        this.ciphertext = ciphertext;
    }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }
    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String ciphertext) { this.ciphertext = ciphertext; }
}
