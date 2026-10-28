import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Benign, bounded probes of the installed Keycloak classes; not an exploit or clearance. */
public class InputPathProbe {
    private static byte[] serialize(Object value) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var stream = new ObjectOutputStream(buffer)) { stream.writeObject(value); }
        return buffer.toByteArray();
    }

    private static Object guavaCollection(String name) throws Exception {
        if (name.equals("MapMakerInternalMap")) {
            var makerClass = Class.forName("com.google.common.collect.MapMaker");
            var maker = makerClass.getConstructor().newInstance();
            makerClass.getMethod("weakKeys").invoke(maker);
            return makerClass.getMethod("makeMap").invoke(maker);
        }
        var factory = Class.forName("com.google.common.collect." + name).getDeclaredMethod("create");
        factory.setAccessible(true);
        return factory.invoke(null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        check(System.getProperty("jdk.serialFilter") == null, "Unexpected global serialization filter");
        check(ObjectInputFilter.Config.getSerialFilter() == null, "Unexpected configured serial filter");
        var method = Class.forName("org.keycloak.common.util.KerberosSerializationUtils")
                .getDeclaredMethod("deserialize", String.class);
        method.setAccessible(true);
        var positive = new HashMap<String, String>();
        positive.put("synthetic", "control");
        check(positive.equals(method.invoke(null, Base64.getEncoder().encodeToString(serialize(positive)))),
                "Kerberos positive control failed");
        System.out.println("PASS Kerberos allowed collection positive control");

        for (String type : List.of("CompactHashMap", "CompactHashSet", "MapMakerInternalMap")) {
            Object value = guavaCollection(type);
            byte[] bytes = serialize(value); // Empty collections only: no hostile size or allocation.
            try (var stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                check(stream.readObject().getClass() == value.getClass(), "Native positive control failed");
            }
            try {
                method.invoke(null, Base64.getEncoder().encodeToString(bytes));
                throw new AssertionError("Kerberos accepted " + type);
            } catch (InvocationTargetException error) {
                check(error.getCause() instanceof InvalidClassException,
                        "Unexpected rejection: " + error.getCause().getClass().getName());
            }
            System.out.println("PASS native " + type + " present; Kerberos filter rejects it");
        }

        var qr = Class.forName("org.keycloak.utils.QRCodeUtils")
                .getMethod("encodeAsQRBytes", String.class, int.class, int.class);
        byte[] png = (byte[]) qr.invoke(null, "Synthetic QR \u0394\u03bf\u03ba\u03b9\u03bc\u03ae <font>not-font-data</font>", 200, 200);
        check(png.length > 24 && png[0] == (byte) 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G',
                "Expected generated PNG");
        System.out.println("PASS QR generation produces PNG; bytes=" + png.length);
        System.out.println("LIMITATION: these probes do not establish whole-server input reachability or approve any finding");
    }
}
