module BJCDCScraper {
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.web;
	requires java.xml;
	
	opens application to javafx.graphics, javafx.fxml;
}
