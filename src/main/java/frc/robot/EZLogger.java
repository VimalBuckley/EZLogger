package frc.robot;

import java.util.HashMap;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.PubSubOption;
import edu.wpi.first.util.sendable.Sendable;
import edu.wpi.first.util.sendable.SendableRegistry;
import edu.wpi.first.util.struct.Struct;
import edu.wpi.first.util.struct.StructBuffer;
import edu.wpi.first.util.struct.StructSerializable;
import edu.wpi.first.wpilibj.smartdashboard.SendableBuilderImpl;

public class EZLogger {
    private static HashMap<LogAccess, Loggable> loggables = new HashMap<>();
    private static HashMap<String, Sendable> sendables = new HashMap<>();
    private static HashMap<String, Struct<?>> registeredSchema = new HashMap<>();
    private static HashMap<String, Number> innerNumbers = new HashMap<>();
    private static HashMap<String, Boolean> innerBooleans = new HashMap<>();
    private static HashMap<String, String> innerStrings = new HashMap<>();
    private static HashMap<String, Sendable> innerSendables = new HashMap<>();
    private static HashMap<String, StructSerializable>  innerSerializables = new HashMap<>();
    private static HashMap<String, StructSerializable[]> innerSerializablesArray = new HashMap<>();
    private static LogAccess innerAccess = new LogAccess("");
    private static Loggable innerLoggable = new Loggable() {
        public void log(LogAccess table) {
            for (String key : innerNumbers.keySet()) {
                table.put(key, innerNumbers.get(key));
            }
            for (String key : innerBooleans.keySet()) {
                table.put(key, innerBooleans.get(key));
            }
            for (String key : innerStrings.keySet()) {
                table.put(key, innerStrings.get(key));
            }
            for (String key : innerSendables.keySet()) {
                table.put(key, innerSendables.get(key));
            }
            for (String key: innerSendables.keySet()) {
                table.put(key, innerSendables.get(key));
            }
            for (String key : innerSerializablesArray.keySet()) {
                table.put(key, innerSerializablesArray.get(key));
            }
        }
    };

    static {
        loggables.put(innerAccess, innerLoggable);
    }

    public static void periodic() {
        for (LogAccess key : loggables.keySet()) {
            loggables.get(key).log(key);
        }
    }

    public static void put(String name, Loggable toLog) {
        loggables.put(new LogAccess(name), toLog);
    }

    public static void put(String key, Sendable value) {
        innerSendables.put(key, value);
    }

    public static <T extends StructSerializable> void put(String key, T value) {
        innerSerializables.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public static <T extends StructSerializable> void put(String key, T... value) {
        innerSerializablesArray.put(key, value);
    }


    public static interface Loggable {
        public void log(LogAccess table);
    }

    private static Struct<?> findStructType(Class<?> classObj) {
        if (!registeredSchema.containsKey(classObj.getName())) {
            registeredSchema.put(classObj.getName(), null);
            Field field = null;
            try {
                field = classObj.getDeclaredField("struct");
            } catch (NoSuchFieldException | SecurityException e) {}
            if (field != null) {
                try {
                    registeredSchema.put(classObj.getName(), (Struct<?>) field.get(null));
                } catch (IllegalArgumentException | IllegalAccessException e) {}
            }
        }
        return registeredSchema.get(classObj.getName());
    }

    public static class LogAccess {
        private NetworkTable table;
        private String name;
        public LogAccess(String name) {
            table = NetworkTableInstance.getDefault().getTable("EZLogger").getSubTable(name);
            this.name = name;
        }

        public void put(String key, Number value) {
            table.getEntry(key).setNumber(value);
        }

        public void put(String key, boolean value) {
            table.getEntry(key).setBoolean(value);
        }

        public void put(String key, String value) {
            table.getEntry(key).setString(value);
        }

        public void put(String key, Loggable value) {
            value.log(new LogAccess(name + "/" + key));
        }

        public void put(String key, Sendable value) {
            if (sendables.get(key) != null) return;
            sendables.put(key, value); 
            NetworkTable dataTable = table.getSubTable(key);
            SendableBuilderImpl builder = new SendableBuilderImpl();
            builder.setTable(dataTable);
            SendableRegistry.publish(value, builder);
            builder.startListeners();
            dataTable.getEntry(".name").setString(key);
        }

        @SuppressWarnings("unchecked")
        public <T extends StructSerializable> void put(String key, T value) {
            Struct<T> struct = (Struct<T>) findStructType(value.getClass());
            NetworkTableInstance.getDefault().addSchema(struct);
            StructBuffer<T> buffer = StructBuffer.create(struct);
            ByteBuffer bytes = buffer.write(value);
            byte[] array = new byte[bytes.position()];
            bytes.position(0);
            bytes.get(array); 
            var publisher = table.getTopic(key).genericPublish(struct.getTypeString(), PubSubOption.sendAll(true));
            publisher.setRaw(array);
            publisher.close();
        }
        
        @SuppressWarnings("unchecked")
        public <T extends StructSerializable> void put(String key, T... value) {
            Struct<T> struct = (Struct<T>) findStructType(value.getClass().getComponentType());
            NetworkTableInstance.getDefault().addSchema(struct);
            StructBuffer<T> buffer = StructBuffer.create(struct);
            ByteBuffer bytes = buffer.writeArray(value);
            byte[] array = new byte[bytes.position()];
            bytes.position(0);
            bytes.get(array); 
            var publisher = table.getTopic(key).genericPublish(struct.getTypeString() + "[]", PubSubOption.sendAll(true));
            publisher.setRaw(array);
            publisher.close();
        }
    }
}