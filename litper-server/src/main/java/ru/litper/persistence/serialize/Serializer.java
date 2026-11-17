package ru.litper.persistence.serialize;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Общий контракт сохранения и загрузки справочника (лабораторная №3).
 *
 * <p>GUI и тесты работают только через этот интерфейс и не знают, что внутри:
 * текстовый JSON или собственный бинарный формат. Добавить новый формат — значит
 * написать ещё одну реализацию.</p>
 *
 * @param <T> тип сохраняемых данных
 */
public interface Serializer<T> {

    /** Краткое имя формата, например {@code json}. */
    String format();

    /** Расширение файла без точки, например {@code json}. */
    String fileExtension();

    /**
     * Сохраняет данные в файл.
     *
     * @throws IOException если файл нельзя записать
     */
    void save(T data, Path file) throws IOException;

    /**
     * Загружает данные из файла.
     *
     * @throws IOException если файл нельзя прочитать или он повреждён
     */
    T load(Path file) throws IOException;
}
