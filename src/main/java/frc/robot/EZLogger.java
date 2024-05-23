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
    private static HashMap<LogTable, Loggable> loggables = new HashMap<>();
    private static HashMap<String, Sendable> sendables = new HashMap<>();
    private static HashMap<String, Struct<?>> registeredSchema = new HashMap<>();

    public static void periodic() {
        for (LogTable key : loggables.keySet()) {
            loggables.get(key).log(key);
        }
    }

    public static void registerLoggable(String name, Loggable toLog) {
        loggables.put(new LogTable(name), toLog);
    }

    public static interface Loggable {
        public void log(LogTable table);
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

    public static class LogTable {
        private NetworkTable table;
        private String name;
        public LogTable(String name) {
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
            value.log(new LogTable(name + "/" + key));
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