import java.io.Serializable;

public class Resposta extends Comunicado implements Serializable {
    private byte[] vetorOrdenado;

    public Resposta(byte[] vetorOrdenado) throws Exception {
        if (vetorOrdenado == null || vetorOrdenado.length == 0) {
            throw new Exception("Vetor ordenado não pode ser nulo ou vazio");
        }
        this.vetorOrdenado = new byte[vetorOrdenado.length];
        System.arraycopy(vetorOrdenado, 0, this.vetorOrdenado, 0, vetorOrdenado.length);
    }

    public byte[] getVetor() {
        byte[] copia = new byte[this.vetorOrdenado.length];
        System.arraycopy(this.vetorOrdenado, 0, copia, 0, this.vetorOrdenado.length);
        return copia;
    }
}
