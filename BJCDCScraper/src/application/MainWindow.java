package application;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class MainWindow implements Initializable {

	@FXML
	private WebView webView;

	@FXML
	private TextField txtURL;
	@FXML
	private TextField txtFinalArticle;
	@FXML
	private TextField txtDirectory;

	@FXML
	private Button btnExecute;
	@FXML
	private Button btnRefresh;
	@FXML
	private Button btnPlus;
	@FXML
	private Button btnMinus;

	private WebEngine engine;

	private double webZoom;
	private int intCurrentPage;
	private int intCurrentArticle;
	private Document dcPageList;
	private boolean isNextPage;
	private boolean isEnd;
	private Timer loadErrorSolver;

	private ChangeListener<Worker.State> listener;

	private static final int SECONDS_IN_MILISECONDS = 1000;
	private static final int RANGE_MILISECONDS_TO_WAIT = 7;
	private static final int BASE_SECONDS = 4;
	private static final int LOAD_ERROR_THRESHHOLD_MILISECONDS = 60 * SECONDS_IN_MILISECONDS;
	private static final Random RAND = new Random();

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		engine = webView.getEngine();
		intCurrentPage = 1;
		engine.load("https://www.bjcdc.org/ManagerAction.do?dispatch=getNewsByType&id=47&childId=47");
		txtURL.setText("https://www.bjcdc.org/ManagerAction.do?dispatch=getNewsByType&id=47&childId=47");
		isEnd = false;
		isNextPage = false;
		loadErrorSolver = new Timer();
	}

	private void setListener(Runnable rabCode) {
		try {
			listener = (obs, oldState, newState) -> {
				if (newState == State.SUCCEEDED) {
					// new page has loaded, process:
					rabCode.run();
				}
			};
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@FXML
	void browse(KeyEvent event) {
		if (event == null || event.getCode().equals(KeyCode.ENTER)) {
			engine.load(txtURL.getText());
		}
	}

	@FXML
	void execute() {
		setListener(() -> processArticle());
		if (!engine.getLocation()
				.equals("https://www.bjcdc.org/ManagerAction.do" + "?dispatch=getNewsByType&id=47&" + "childId=47")) {
			engine.load("https://www.bjcdc.org/ManagerAction.do?dispatch=getNewsByType&id=47&childId=47");
		} else {
			processArticle();
		}
	}

	private void processListPage() {
		loadErrorSolver.cancel();
		engine.getLoadWorker().stateProperty().removeListener(listener);
		if (dcPageList == null || isNextPage) {
			dcPageList = engine.getDocument();
			isNextPage = false;
			if (dcPageList == null) {
				System.out.println("請耐心等待頁面加載。");
				return;
			}
		}

		NodeList nlLI = dcPageList.getElementsByTagName("LI");
		if (intCurrentArticle >= nlLI.getLength()) {
			engine.getLoadWorker().stateProperty().removeListener(listener);
			setListener(() -> processNextPage());
			engine.getLoadWorker().stateProperty().addListener(listener);
			intCurrentPage++;
			intCurrentArticle = 0;
			engine.load("https://www.bjcdc.org/ManagerAction.do?dispatch=getNewsByType&id=47&childId=47");
			txtURL.setText("https://www.bjcdc.org/ManagerAction.do?dispatch=getNewsByType&id=47&childId=47");
		} else {
			Element elementA = (Element) ((Element) nlLI.item(intCurrentArticle)).getElementsByTagName("A").item(0);
			intCurrentArticle++;
			String strArticleLink = "https://www.bjcdc.org/" + elementA.getAttribute("href");

			try {
				Thread.sleep((BASE_SECONDS + RAND.nextInt(RANGE_MILISECONDS_TO_WAIT)) * SECONDS_IN_MILISECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (!"".equals(elementA.getTextContent().trim())) {
				engine.getLoadWorker().stateProperty().removeListener(listener);
				isEnd = elementA == null || elementA.getTextContent().contains(txtFinalArticle.getText());
				setListener(() -> processArticle());
				engine.getLoadWorker().stateProperty().addListener(listener);
				engine.load(strArticleLink);
				txtURL.setText(strArticleLink);
				loadErrorSolver = new Timer(false);
				loadErrorSolver.schedule(new TimerTask() {
					@Override
					public void run() {
						Platform.runLater(() -> {
							engine.load(txtURL.getText());
						});
					}
				}, LOAD_ERROR_THRESHHOLD_MILISECONDS, LOAD_ERROR_THRESHHOLD_MILISECONDS);
			} else {
				processListPage();
			}
		}
	}

	private void processArticle() {
		Document dcArticle = engine.getDocument();

		NodeList nlLI = dcArticle.getElementsByTagName("LI");
		NodeList nlP = dcArticle.getElementsByTagName("P");

		String strDate = nlLI.item(2).getTextContent();
		strDate = strDate.substring(strDate.indexOf('：') + 1);
		String[] arrstrDate = strDate.split("-", -1);

		String strArticle = "{{Header|\n|y=" + arrstrDate[0] + "|m=" + arrstrDate[1] + "|d=" + arrstrDate[2]
				+ "|lawmaker=北京市疾病预防控制中心\n|notes=" + engine.getLocation() + "\n}}\n";

		for (int i = 0; i < nlP.getLength(); i++) {
			if (i == 0) {
				strArticle += "'''" + nlP.item(i).getTextContent().trim() + "'''\n\n";
			} else {
				strArticle += nlP.item(i).getTextContent().trim() + "\n\n";
			}
		}

		strArticle = strArticle.trim() + "\n{{PD-PRC-exempt}}";

		try {
			InputStream input = new ByteArrayInputStream(strArticle.getBytes("UTF-8"));
			int length = 0;
			FileOutputStream fp = new FileOutputStream(
					txtDirectory.getText() + "\\" + nlLI.item(1).getTextContent() + ".txt");
			byte[] buffer = new byte[2048];
			while ((length = input.read(buffer)) != -1) {
				fp.write(buffer, 0, length);
			}
			fp.close();
			input.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		processListPage();
	}

	private void processNextPage() {
		isNextPage = true;
		loadErrorSolver.cancel();
		engine.getLoadWorker().stateProperty().removeListener(listener);
		setListener(() -> processListPage());
		engine.getLoadWorker().stateProperty().addListener(listener);
		try {
			Thread.sleep((BASE_SECONDS + RAND.nextInt(RANGE_MILISECONDS_TO_WAIT)) * SECONDS_IN_MILISECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		intCurrentPage++;
		if (isEnd) {
			return;
		}
		engine.load("https://www.bjcdc.org/ManagerAction.do?dispatch=gotoPage2&pageNum=" + intCurrentPage);
		loadErrorSolver = new Timer(false);
		loadErrorSolver.schedule(new TimerTask() {
			@Override
			public void run() {
				Platform.runLater(() -> {
					engine.load(txtURL.getText());
				});
			}
		}, LOAD_ERROR_THRESHHOLD_MILISECONDS, LOAD_ERROR_THRESHHOLD_MILISECONDS);
	}

	public void refresh() {
		engine.reload();
	}

	public void zoomIn() {

		webZoom += 0.1;
		webView.setZoom(webZoom);
	}

	public void zoomOut() {
		webZoom -= 0.1;
		webView.setZoom(webZoom);
	}
}
