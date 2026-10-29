package br.com.alicerce.saida;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Faixa de endereços em notação CIDR, IPv4 ou IPv6. */
public record Rede(InetAddress base, int prefixo) {

    public Rede {
        int bits = base.getAddress().length * 8;
        if (prefixo < 0 || prefixo > bits) {
            throw new IllegalArgumentException("prefixo fora da faixa: /" + prefixo);
        }
    }

    public static Rede de(String cidr) {
        String[] partes = cidr.split("/", 2);
        if (partes.length != 2) {
            throw new IllegalArgumentException("esperava endereço/prefixo: " + cidr);
        }
        try {
            // Só literal: aceitar nome aqui faria a política depender de DNS.
            if (!partes[0].matches("[0-9a-fA-F:.]+")) {
                throw new IllegalArgumentException("esperava endereço IP, não nome: " + cidr);
            }
            return new Rede(InetAddress.getByName(partes[0]), Integer.parseInt(partes[1]));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("endereço inválido: " + cidr, e);
        }
    }

    public boolean contem(InetAddress endereco) {
        byte[] alvo = endereco.getAddress();
        byte[] rede = base.getAddress();
        if (alvo.length != rede.length) {
            return false;
        }
        int bits = rede.length * 8;
        BigInteger mascara = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)
                .shiftRight(prefixo).not().and(BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE));
        return new BigInteger(1, alvo).and(mascara).equals(new BigInteger(1, rede).and(mascara));
    }
}
