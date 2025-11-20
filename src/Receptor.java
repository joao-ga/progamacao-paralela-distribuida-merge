import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Receptor {

    public static final int PORTA_PADRAO = 12345;

    public static void main(String[] args) {
        int porta = PORTA_PADRAO;
        if (args.length == 1) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida, usando porta padrão " + PORTA_PADRAO);
                porta = PORTA_PADRAO;
            }
        } else if (args.length > 1) {
            System.err.println("Uso esperado: java Receptor [PORTA]");
            return;
        }

        System.out.printf("[R] Iniciando servidor na porta %d%n", porta);

        try (ServerSocket servidor = new ServerSocket(porta)) {
            for (;;) {
                Socket conexao = servidor.accept();
                String ip = conexao.getInetAddress().getHostAddress();
                System.out.printf("[R] Nova conexão de %s%n", ip);

                Thread t = new Thread(() -> atenderCliente(conexao));
                t.start();
            }
        } catch (IOException e) {
            System.err.println("[R] Erro no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void atenderCliente(Socket conexao) {
        String ip = conexao.getInetAddress().getHostAddress();
        try (
                ObjectOutputStream transmissor = new ObjectOutputStream(conexao.getOutputStream());
                ObjectInputStream receptor = new ObjectInputStream(conexao.getInputStream())
        ) {
            for (;;) {
                Object obj;
                try {
                    obj = receptor.readObject();
                } catch (EOFException eof) {
                    System.out.printf("[R] Cliente %s encerrou a conexão%n", ip);
                    break;
                }

                if (obj instanceof ComunicadoEncerramento) {
                    System.out.printf("[R] ComunicadoEncerramento recebido de %s. Fechando conexão.%n", ip);
                    break;
                }

                if (!(obj instanceof Pedido)) {
                    System.err.printf("[R] Objeto desconhecido de %s: %s%n", ip,
                            obj == null ? "null" : obj.getClass().getName());
                    continue;
                }

                Pedido pedido = (Pedido) obj;
                byte[] numeros = pedido.getNumeros();
                System.out.printf("[R] Pedido recebido de %s com %d elementos%n", ip, numeros.length);

                // Ordenação paralela interna neste receptor
                byte[] ordenado = ordenarParalelo(numeros);

                Resposta resposta = new Resposta(ordenado);
                transmissor.writeObject(resposta);
                transmissor.flush();
                System.out.printf("[R] Resposta enviada para %s%n", ip);
            }
        } catch (Exception e) {
            System.err.printf("[R] Erro ao atender cliente %s: %s%n", ip, e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                conexao.close();
            } catch (IOException e) {
                // ignora
            }
        }
    }

    // Divide o vetor em partes e usa várias threads para ordenar com merge sort
    private static byte[] ordenarParalelo(byte[] numeros) {
        int n = numeros.length;
        int nProc = Runtime.getRuntime().availableProcessors();
        if (nProc < 2 || n < 2 * nProc) {
            // Pequeno demais para paralelizar – faz sequencial
            byte[] copia = numeros.clone();
            mergeSort(copia, 0, copia.length - 1);
            return copia;
        }

        int partes = nProc;
        Thread[] threads = new Thread[partes];
        int[] ini = new int[partes];
        int[] fim = new int[partes];

        int base = n / partes;
        int resto = n % partes;
        int inicio = 0;

        for (int i = 0; i < partes; i++) {
            int tam = base + (i < resto ? 1 : 0);
            ini[i] = inicio;
            fim[i] = inicio + tam - 1;
            inicio += tam;
        }

        // Ordena cada parte em paralelo (in place no mesmo array)
        for (int i = 0; i < partes; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> mergeSort(numeros, ini[idx], fim[idx]));
            threads[i].start();
        }

        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[R] join interrompido");
        }

        // Agora faz merges sucessivos até sobrar um único segmento ordenado
        int segmentos = partes;
        while (segmentos > 1) {
            int novoSegmentos = 0;
            for (int i = 0; i < segmentos; i += 2) {
                if (i + 1 < segmentos) {
                    merge(numeros, ini[i], fim[i], fim[i + 1]);
                    fim[i / 2] = fim[i + 1];
                    ini[i / 2] = ini[i];
                } else {
                    ini[i / 2] = ini[i];
                    fim[i / 2] = fim[i];
                }
                novoSegmentos++;
            }
            segmentos = novoSegmentos;
        }

        // copia final (poderia devolver o próprio array também)
        byte[] resultado = new byte[n];
        System.arraycopy(numeros, 0, resultado, 0, n);
        return resultado;
    }

    // Merge sort recursivo tradicional
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
