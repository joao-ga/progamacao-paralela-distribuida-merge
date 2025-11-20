import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;

public class Distribuidor {

    // Coloque aqui os IPs das máquinas que executarão o Receptor
    // Para teste local: executar vários Receptores em portas diferentes
    private static final String[] HOSTS  = { "127.0.0.1", "127.0.0.1" };
    private static final int[]    PORTAS = { 12345,       12346       };

    private static class Worker extends Thread {
        private final String host;
        private final int porta;
        private final byte[] parte;
        private byte[] resultado;

        public Worker(String host, int porta, byte[] parte) {
            this.host = host;
            this.porta = porta;
            this.parte = parte;
        }

        public byte[] getResultado() {
            return resultado;
        }

        @Override
        public void run() {
            try (
                    Socket socket = new Socket(host, porta);
                    ObjectOutputStream transmissor = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream receptor = new ObjectInputStream(socket.getInputStream())
            ) {
                System.out.printf("[D] Conectado a %s:%d, enviando pedido com %d elementos%n",
                        host, porta, parte.length);

                Pedido pedido = new Pedido(parte);
                transmissor.writeObject(pedido);
                transmissor.flush();

                Object obj = receptor.readObject();
                if (!(obj instanceof Resposta)) {
                    throw new IOException("Objeto inesperado recebido do servidor");
                }
                Resposta resposta = (Resposta) obj;
                this.resultado = resposta.getVetor();

                // Envia comunicado de encerramento para este Receptor
                transmissor.writeObject(new ComunicadoEncerramento());
                transmissor.flush();
                System.out.printf("[D] Encerramento enviado para %s:%d%n", host, porta);

            } catch (Exception e) {
                System.err.printf("[D] Erro no Worker para %s:%d -> %s%n",
                        host, porta, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        if (HOSTS.length == 0 || HOSTS.length != PORTAS.length) {
            System.err.println("[D] Configure HOSTS/PORTAS corretamente.");
            return;
        }

        try {
            System.out.println("=== SISTEMA DISTRIBUÍDO DE ORDENAÇÃO (MERGE SORT) ===");

            int tamanho = 200_000; // valor padrão razoável
            System.out.printf("Tamanho padrão do vetor: %,d bytes%n", tamanho);
            System.out.print("Pressione ENTER para usar o padrão ou digite outro tamanho: ");
            try {
                String linha = Teclado.getUmString();
                if (linha != null && !linha.trim().isEmpty()) {
                    tamanho = Integer.parseInt(linha.trim());
                }
            } catch (Exception e) {
                System.out.println("Entrada inválida, usando tamanho padrão.");
            }

            // gera vetor original
            byte[] original = gerarVetorAleatorio(tamanho);
            byte[] copiaSequencial = Arrays.copyOf(original, original.length);
            byte[] copiaDistribuida = Arrays.copyOf(original, original.length);

            // Ordenação sequencial local
            long iniSeq = System.currentTimeMillis();
            mergeSortSequencial(copiaSequencial, 0, copiaSequencial.length - 1);
            long fimSeq = System.currentTimeMillis();
            long tempoSeq = fimSeq - iniSeq;
            System.out.printf("Tempo SEQUENCIAL: %d ms%n", tempoSeq);

            // Ordenação distribuída
            long iniDist = System.currentTimeMillis();
            byte[] ordenadoDistribuido = ordenarDistribuido(copiaDistribuida);
            long fimDist = System.currentTimeMillis();
            long tempoDist = fimDist - iniDist;
            System.out.printf("Tempo DISTRIBUÍDO: %d ms%n", tempoDist);

            // Gravar resultado em arquivo texto
            System.out.print("Informe o nome do arquivo para gravar o vetor ordenado: ");
            String nomeArq = Teclado.getUmString();
            if (nomeArq == null || nomeArq.trim().isEmpty()) {
                nomeArq = "resultado.txt";
            }
            gravarEmArquivo(nomeArq, ordenadoDistribuido);
            System.out.println("Vetor ordenado gravado em: " + nomeArq);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] gerarVetorAleatorio(int n) {
        Random rnd = new Random();
        byte[] v = new byte[n];
        rnd.nextBytes(v);
        return v;
    }

    // Ordenação distribuída: divide o vetor entre os servidores, junta respostas com merge
    private static byte[] ordenarDistribuido(byte[] vetor) throws InterruptedException {
        int nServidores = HOSTS.length;
        if (nServidores == 0) {
            throw new IllegalStateException("Nenhum servidor configurado");
        }

        Worker[] workers = new Worker[nServidores];

        int n = vetor.length;
        int base = n / nServidores;
        int resto = n % nServidores;
        int inicio = 0;

        for (int i = 0; i < nServidores; i++) {
            int tam = base + (i < resto ? 1 : 0);
            byte[] parte = Arrays.copyOfRange(vetor, inicio, inicio + tam);
            inicio += tam;

            workers[i] = new Worker(HOSTS[i], PORTAS[i], parte);
            workers[i].start();
        }

        for (Worker w : workers) {
            w.join();
        }

        // Junta todos os vetores ordenados com merges sucessivos
        byte[][] partesOrdenadas = new byte[nServidores][];
        for (int i = 0; i < nServidores; i++) {
            partesOrdenadas[i] = workers[i].getResultado();
            if (partesOrdenadas[i] == null) {
                throw new IllegalStateException("Worker " + i + " não retornou resultado");
            }
        }

        return mergeTodos(partesOrdenadas);
    }

    // Merge sort sequencial, usado tanto no comparativo quanto como base
    private static void mergeSortSequencial(byte[] v, int ini, int fim) {
        if (ini >= fim) return;
        int meio = (ini + fim) / 2;
        mergeSortSequencial(v, ini, meio);
        mergeSortSequencial(v, meio + 1, fim);
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

    // Faz merge de todos os vetores parciais
    private static byte[] mergeTodos(byte[][] partes) {
        if (partes.length == 1) return partes[0];

        byte[] atual = partes[0];
        for (int i = 1; i < partes.length; i++) {
            atual = mergeDois(atual, partes[i]);
        }
        return atual;
    }

    private static byte[] mergeDois(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        int i = 0, j = 0, k = 0;
        while (i < a.length && j < b.length) {
            if (a[i] <= b[j]) c[k++] = a[i++];
            else c[k++] = b[j++];
        }
        while (i < a.length) c[k++] = a[i++];
        while (j < b.length) c[k++] = b[j++];
        return c;
    }

    private static void gravarEmArquivo(String nome, byte[] v) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(nome))) {
            for (byte b : v) {
                bw.write(Byte.toString(b));
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erro ao gravar arquivo: " + e.getMessage());
        }
    }
}
