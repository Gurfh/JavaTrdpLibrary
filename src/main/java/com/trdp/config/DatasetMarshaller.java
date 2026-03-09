package com.trdp.config;

import com.trdp.util.TrdpDataType;
import com.trdp.util.TrdpDataset;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that maps ComIDs to dataset schemas and provides automatic
 * marshalling (encode) and unmarshalling (decode) of structured payloads.
 * <p>
 * Built from a {@link DeviceConfig}, it resolves each telegram's
 * {@code data-set-id} to its {@link DataSetDefinition} and converts the
 * element types into {@link TrdpDataType}-based field schemas. Supports
 * nested dataset references and array elements.
 * <p>
 * Usage:
 * <pre>{@code
 * DeviceConfig config = TrdpConfig.load(Path.of("trdp-config.xml"));
 * DatasetMarshaller marshaller = DatasetMarshaller.from(config);
 *
 * // Encode
 * byte[] payload = marshaller.marshall(1000, Map.of("speed", 42L, "doorOpen", true));
 *
 * // Decode
 * TrdpDataset dataset = marshaller.unmarshall(1000, receivedBytes);
 * long speed = (Long) dataset.getValue("speed");
 * }</pre>
 *
 * @see TrdpConfig
 * @see TrdpDataset
 */
public class DatasetMarshaller {

    private final Map<Integer, List<TrdpDataset.FieldDefinition>> schemas;

    private DatasetMarshaller(Map<Integer, List<TrdpDataset.FieldDefinition>> schemas) {
        this.schemas = schemas;
    }

    /**
     * Builds a marshaller from a device configuration. Resolves all telegram
     * ComID-to-dataset mappings across all bus interfaces.
     *
     * @param config the parsed device configuration
     * @return a marshaller with schemas registered for all telegrams that reference datasets
     */
    public static DatasetMarshaller from(DeviceConfig config) {
        Map<Integer, DataSetDefinition> datasetById = new HashMap<>();
        for (DataSetDefinition ds : config.getDataSets()) {
            datasetById.put(ds.getId(), ds);
        }

        Map<Integer, List<TrdpDataset.FieldDefinition>> schemas = new HashMap<>();
        for (BusInterface bi : config.getBusInterfaces()) {
            for (TelegramConfig telegram : bi.getTelegrams()) {
                Long dsId = telegram.getDataSetId();
                if (dsId == null) continue;
                DataSetDefinition dsDef = datasetById.get(dsId.intValue());
                if (dsDef == null) continue;

                List<TrdpDataset.FieldDefinition> schema = buildSchema(dsDef, datasetById);
                schemas.put((int) telegram.getComId(), schema);
            }
        }

        return new DatasetMarshaller(schemas);
    }

    /**
     * Encodes field values into a binary payload for the given ComID.
     * Values are encoded in the order defined by the dataset schema.
     * Missing fields default to zero/false/epoch.
     *
     * @param comId  the communication ID
     * @param values field name to value mapping
     * @return the encoded binary payload
     * @throws IllegalArgumentException if no dataset is registered for the ComID
     */
    public byte[] marshall(int comId, Map<String, Object> values) {
        List<TrdpDataset.FieldDefinition> schema = requireSchema(comId);
        TrdpDataset dataset = new TrdpDataset();
        for (TrdpDataset.FieldDefinition def : schema) {
            Object value = values.get(def.getName());
            if (value == null) {
                addDefaultValue(dataset, def);
            } else {
                addValue(dataset, def, value);
            }
        }
        return dataset.encode();
    }

    /**
     * Decodes a binary payload into a {@link TrdpDataset} for the given ComID.
     *
     * @param comId the communication ID
     * @param data  the binary payload to decode
     * @return the decoded dataset with named fields
     * @throws IllegalArgumentException if no dataset is registered for the ComID
     */
    public TrdpDataset unmarshall(int comId, byte[] data) {
        List<TrdpDataset.FieldDefinition> schema = requireSchema(comId);
        return TrdpDataset.decode(data, schema);
    }

    /**
     * Returns whether a dataset schema is registered for the given ComID.
     *
     * @param comId the communication ID
     * @return true if a schema exists
     */
    public boolean hasSchema(int comId) {
        return schemas.containsKey(comId);
    }

    /**
     * Returns the field schema for the given ComID.
     *
     * @param comId the communication ID
     * @return unmodifiable list of field definitions
     * @throws IllegalArgumentException if no dataset is registered for the ComID
     */
    public List<TrdpDataset.FieldDefinition> getSchema(int comId) {
        return Collections.unmodifiableList(requireSchema(comId));
    }

    private List<TrdpDataset.FieldDefinition> requireSchema(int comId) {
        List<TrdpDataset.FieldDefinition> schema = schemas.get(comId);
        if (schema == null) {
            throw new IllegalArgumentException("No dataset registered for ComID " + comId);
        }
        return schema;
    }

    private static List<TrdpDataset.FieldDefinition> buildSchema(
            DataSetDefinition dsDef, Map<Integer, DataSetDefinition> datasetById) {
        List<TrdpDataset.FieldDefinition> schema = new ArrayList<>();
        for (DataSetElement elem : dsDef.getElements()) {
            String typeName = elem.getType();
            int arraySize = (int) elem.getArraySize();

            // Check if type is a nested dataset reference (numeric ID)
            try {
                int nestedId = Integer.parseInt(typeName);
                DataSetDefinition nested = datasetById.get(nestedId);
                if (nested != null) {
                    List<TrdpDataset.FieldDefinition> nestedSchema = buildSchema(nested, datasetById);
                    for (int i = 0; i < arraySize; i++) {
                        for (TrdpDataset.FieldDefinition nestedDef : nestedSchema) {
                            String name = arraySize > 1
                                    ? elem.getName() + "[" + i + "]." + nestedDef.getName()
                                    : elem.getName() + "." + nestedDef.getName();
                            schema.add(new TrdpDataset.FieldDefinition(name, nestedDef.getType()));
                        }
                    }
                    continue;
                }
            } catch (NumberFormatException e) {
                // Not a numeric type, treat as TrdpDataType name
            }

            TrdpDataType dataType = TrdpDataType.valueOf(typeName);
            for (int i = 0; i < arraySize; i++) {
                String fieldName = arraySize > 1
                        ? elem.getName() + "[" + i + "]"
                        : elem.getName();
                schema.add(new TrdpDataset.FieldDefinition(fieldName, dataType));
            }
        }
        return schema;
    }

    private static void addDefaultValue(TrdpDataset dataset, TrdpDataset.FieldDefinition def) {
        switch (def.getType()) {
            case BOOL8 -> dataset.addBool8(def.getName(), false);
            case CHAR8 -> dataset.addChar8(def.getName(), '\0');
            case INT8 -> dataset.addInt8(def.getName(), (byte) 0);
            case INT16 -> dataset.addInt16(def.getName(), (short) 0);
            case INT32 -> dataset.addInt32(def.getName(), 0);
            case INT64 -> dataset.addInt64(def.getName(), 0L);
            case UINT8 -> dataset.addUInt8(def.getName(), 0);
            case UINT16 -> dataset.addUInt16(def.getName(), 0);
            case UINT32 -> dataset.addUInt32(def.getName(), 0L);
            case REAL32 -> dataset.addReal32(def.getName(), 0.0f);
            case REAL64 -> dataset.addReal64(def.getName(), 0.0);
            case TIMEDATE64 -> dataset.addTimeDate64(def.getName(), Instant.EPOCH);
            default -> throw new IllegalStateException("Unsupported type: " + def.getType());
        }
    }

    private static void addValue(TrdpDataset dataset, TrdpDataset.FieldDefinition def, Object value) {
        switch (def.getType()) {
            case BOOL8 -> dataset.addBool8(def.getName(), (Boolean) value);
            case CHAR8 -> dataset.addChar8(def.getName(), (Character) value);
            case INT8 -> dataset.addInt8(def.getName(), ((Number) value).byteValue());
            case INT16 -> dataset.addInt16(def.getName(), ((Number) value).shortValue());
            case INT32 -> dataset.addInt32(def.getName(), ((Number) value).intValue());
            case INT64 -> dataset.addInt64(def.getName(), ((Number) value).longValue());
            case UINT8 -> dataset.addUInt8(def.getName(), ((Number) value).intValue());
            case UINT16 -> dataset.addUInt16(def.getName(), ((Number) value).intValue());
            case UINT32 -> dataset.addUInt32(def.getName(), ((Number) value).longValue());
            case REAL32 -> dataset.addReal32(def.getName(), ((Number) value).floatValue());
            case REAL64 -> dataset.addReal64(def.getName(), ((Number) value).doubleValue());
            case TIMEDATE64 -> dataset.addTimeDate64(def.getName(), (Instant) value);
            default -> throw new IllegalStateException("Unsupported type: " + def.getType());
        }
    }
}
