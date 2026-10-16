module ru.litper {
    requires javafx.controls;
    requires com.fasterxml.jackson.databind;

    exports ru.litper.gui to javafx.graphics;

    // Jackson читает и создаёт DTO-записи через reflection, поэтому пакет нужно открыть.
    opens ru.litper.persistence.serialize to com.fasterxml.jackson.databind;
}