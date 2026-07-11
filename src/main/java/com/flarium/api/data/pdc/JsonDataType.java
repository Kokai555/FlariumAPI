package com.flarium.api.data.pdc;

import com.google.gson.Gson;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;

public class JsonDataType<T> implements PersistentDataType<String, T> {

    private static final Gson GSON = new Gson();
    private final Class<T> type;

    public JsonDataType(Class<T> type) {
        this.type = type;
    }

    @Override
    public Class<String> getPrimitiveType() {
        return String.class;
    }

    @Override
    public Class<T> getComplexType() {
        return type;
    }

    @Override
    public String toPrimitive(T complex, PersistentDataAdapterContext context) {
        return GSON.toJson(complex, type);
    }

    @Override
    public T fromPrimitive(String primitive, PersistentDataAdapterContext context) {
        return GSON.fromJson(primitive, type);
    }
}