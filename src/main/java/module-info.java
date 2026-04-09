module com.discord.discord_lite {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;


    opens com.discord.discord_lite to javafx.fxml;
    opens com.discord.discord_lite.model to com.fasterxml.jackson.databind;
    opens com.discord.discord_lite.network to com.fasterxml.jackson.databind;
    exports com.discord.discord_lite;
}
