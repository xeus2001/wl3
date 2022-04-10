/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.umpani.wl3;

import static java.lang.System.exit;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.impl.VanillaChunkEncoder;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class App {

  static final boolean DEBUG = false;

  static class Wl3 {

    final LinkedHashMap<String, String> header = new LinkedHashMap<>();
    String content;
    int i = 0;

    void readHeader(byte[] bytes) {
      header.clear();
      final StringBuilder sb = new StringBuilder();
      int i = 0;
      while (bytes[i] >= LZFChunk.MAX_LITERAL && bytes[i] != '<') {
        sb.setLength(0);
        byte b = bytes[i++];
        while (b != ':' && b != '\n') {
          sb.append((char) b);
          b = bytes[i++];
        }
        final String key = sb.toString();
        sb.setLength(0);

        final String value;
        if (b == ':') {
          b = bytes[i++];
          assert b == '=';
          b = bytes[i++];
          while (b != '\n') {
            sb.append((char) b);
            b = bytes[i++];
          }
          value = sb.toString();
        } else {
          value = null;
        }
        header.put(key, value);
        if (DEBUG) {
          System.out.println(key + ":=" + value);
        }
      }
      this.i = i;
    }

    void readFile(String filename) throws Exception {
      final byte[] bytes = Files.readAllBytes(Paths.get(filename));
      readHeader(bytes);
      final int start = i;
      final int SaveDataSize = bytes.length - i;
      assert SaveDataSize == Integer.parseInt(header.get("SaveDataSize"));
      final int DataSize = Integer.parseInt(header.get("DataSize"));
      if (bytes[i] == '<') {
        content = new String(bytes, i, bytes.length - i, StandardCharsets.UTF_8);
      } else {
        final byte[] out = new byte[DataSize];
        final ChunkDecoder decoder = LZFDecoder.fastDecoder();
        decoder.decodeChunk(bytes, start, out, 0, out.length);
        content = new String(out, StandardCharsets.UTF_8);
      }
      if (DEBUG) {
        System.out.println(content);
      }
    }

    void writePlain(String filename) throws Exception {
      final byte[] bytes = toPlain().getBytes(StandardCharsets.UTF_8);
      //noinspection ReadWriteStringCanBeUsed
      Files.write(Paths.get(filename), bytes, CREATE, WRITE, TRUNCATE_EXISTING);
    }

    static class Encoder extends VanillaChunkEncoder {

      public Encoder() {
        super(16 * 1024 * 1024);
      }

      @Override
      public int tryCompress(byte[] in, int inPos, int inEnd, byte[] out, int outPos) {
        return super.tryCompress(in, inPos, inEnd, out, outPos);
      }
    }

    void writeCompressed(String filename) throws Exception {
      final byte[] plainBytes = compactXml(content).getBytes(StandardCharsets.UTF_8);
      final Encoder encoder = new Encoder();
      final byte[] contentBytes = new byte[16 * 1024 * 1024];
      final int SaveDataSize = encoder.tryCompress(plainBytes, 0, plainBytes.length, contentBytes,
          0);
      header.put("SaveDataSize", Integer.toString(SaveDataSize));
      header.put("DataSize", Integer.toString(plainBytes.length));
      final StringBuilder sb = new StringBuilder();
      writeHeader(sb);
      final byte[] headerBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
      final byte[] out = new byte[headerBytes.length + SaveDataSize];
      System.arraycopy(headerBytes, 0, out, 0, headerBytes.length);
      System.arraycopy(contentBytes, 0, out, headerBytes.length, SaveDataSize);
      Files.write(Paths.get(filename), out, CREATE, WRITE, TRUNCATE_EXISTING);
    }

    void writeHeader(final @Nonnull StringBuilder sb) {
      for (final Map.Entry<String, String> entry : header.entrySet()) {
        final String key = entry.getKey();
        final String value = entry.getValue();
        sb.append(key);
        if (value != null) {
          sb.append(":=").append(value);
        }
        sb.append('\n');
      }
    }

    String toPlain() throws Exception {
      final StringBuilder sb = new StringBuilder();
      writeHeader(sb);
      sb.append(prettyPrintXml(content));
      return sb.toString();
    }

    String toCompact() throws Exception {
      final StringBuilder sb = new StringBuilder();
      writeHeader(sb);
      sb.append(compactXml(content));
      return sb.toString();
    }

    String prettyPrintXml(String xml) throws Exception {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      InputSource is = new InputSource(new StringReader(xml));
      Document doc = db.parse(is);

      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      StreamResult result = new StreamResult(new StringWriter());
      DOMSource source = new DOMSource(doc);
      transformer.transform(source, result);
      final String text = result.getWriter().toString();
      return text;
    }

    public static void trimWhitespace(Node node)
    {
      NodeList children = node.getChildNodes();
      for(int i = 0; i < children.getLength(); ++i) {
        Node child = children.item(i);
        if(child.getNodeType() == Node.TEXT_NODE) {
          child.setTextContent(child.getTextContent().trim());
        }
        trimWhitespace(child);
      }
    }

    String compactXml(String xml) throws Exception {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      InputSource is = new InputSource(new StringReader(xml));
      Document doc = db.parse(is);
      trimWhitespace(doc);

      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      StreamResult result = new StreamResult(new StringWriter());
      DOMSource source = new DOMSource(doc);
      transformer.transform(source, result);
      final String text = result.getWriter().toString();
      return text;
    }
  }

  static void printHelp() {
    System.out.println("Syntax: app [operation] [args]");
    System.out.println(" decode <filename>");
    System.out.println("  Decode the file <filename>.xml into <filename>.edit.xml");
    System.out.println(" encode <filename>");
    System.out.println("  Encode the file <filename>.edit.xml into <filename>.xml");
  }

  public static void main(String... args) throws Exception {
    if (args.length < 2) {
      printHelp();
      exit(1);
    }
    final Wl3 file = new Wl3();
    final String command = args[0];
    final String filename = args[1];
    if ("decode".equalsIgnoreCase(command)) {
      file.readFile(filename + ".xml");
      file.writePlain(filename + ".edit.xml");
    } else if ("encode".equalsIgnoreCase(command)) {
      file.readFile(filename + ".edit.xml");
      file.writeCompressed(filename + ".xml");
    } else {
      printHelp();
      exit(1);
    }
  }
}