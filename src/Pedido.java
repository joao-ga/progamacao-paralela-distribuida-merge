import java.io.Serializable;

public class Pedido extends Comunicado implements Serializable {
    private byte[] numeros;

    public Pedido(byte[] numeros_) throws Exception {
        if (numeros_ == null || numeros_.length == 0) {
            throw new Exception("Vetor de números não pode ser nulo ou vazio");
        }
        // Cópia defensiva
        this.numeros = new byte[numeros_.length];
        System.arraycopy(numeros_, 0, this.numeros, 0, numeros_.length);
    }

    // Ordena o próprio vetor usando Merge Sort sequencial
    public void ordenar() {
        mergeSort(this.numeros, 0, this.numeros.length - 1);
    }

    public byte[] getNumeros() {
        // devolve cópia para não expor o array interno
        byte[] copia = new byte[this.numeros.length];
        System.arraycopy(this.numeros, 0, copia, 0, this.numeros.length);
        return copia;
    }

    // Implementação recursiva de Merge Sort
    private static void mergeSort(byte[] v, int ini, int fim) {
        if (ini >= fim) return;
        int meio = (ini + fim) / 2;
        mergeSort(v, ini, meio);
        mergeSort(v, meio + 1, fim);
        merge(v, ini, meio, fim);
    }

    private static void merge(byte[] v, int ini, int meio, int fim) {
        int n1 = meio - ini + 1;
        int n2 = fim - meio;

        byte[] e = new byte[n1];
        byte[] d = new byte[n2];

        System.arraycopy(v, ini, e, 0, n1);
        System.arraycopy(v, meio + 1, d, 0, n2);

        int i = 0, j = 0, k = ini;
        while (i < n1 && j < n2) {
            if (e[i] <= d[j]) {
                v[k++] = e[i++];
            } else {
                v[k++] = d[j++];
            }
        }

        while (i < n1) v[k++] = e[i++];
        while (j < n2) v[k++] = d[j++];
    }
}
