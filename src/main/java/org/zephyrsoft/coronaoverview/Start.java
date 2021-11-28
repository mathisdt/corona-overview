package org.zephyrsoft.coronaoverview;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

public class Start {

	private static final int DAYS = 10;

	private static final DateTimeFormatter RKI_EXCEL_INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final DateTimeFormatter RKI_CSV_INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy");
	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy, HH:mm");
	private static final NumberFormat INCIDENCE_NUMBER_FORMAT = NumberFormat.getNumberInstance();
	private static final NumberFormat ID_NUMBER_FORMAT = NumberFormat.getNumberInstance();

	static {
		INCIDENCE_NUMBER_FORMAT.setMinimumFractionDigits(1);
		INCIDENCE_NUMBER_FORMAT.setMaximumFractionDigits(1);
		ID_NUMBER_FORMAT.setMinimumFractionDigits(0);
		ID_NUMBER_FORMAT.setMaximumFractionDigits(0);
		ID_NUMBER_FORMAT.setGroupingUsed(false);
	}

	private List<String> locations;

	public static void main(final String[] args) {
		new Start(List.of(args));
	}

	private Start(final List<String> locations) {
		this.locations = locations;
		if (locations.isEmpty()) {
			System.err.println("no locations given");
		} else {
			doWork();
		}
	}

	private void doWork() {
		try (InputStream fallinzidenzStream = new URL(
			"https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Daten/Fallzahlen_Kum_Tab.xlsx?__blob=publicationFile")
				.openStream();
			InputStream hospitalisierungStream = new URL(
				"https://raw.githubusercontent.com/robert-koch-institut/COVID-19-Hospitalisierungen_in_Deutschland/master/Aktuell_Deutschland_COVID-19-Hospitalisierungen.csv")
					.openStream()) {

			Map<String, Map<LocalDate, Double>> fallinzidenzProOrt = loadFallinzidenzProOrt(fallinzidenzStream);
			Map<LocalDate, Double> hospitalisierungNiedersachsen = loadHospitalisierungsinzidenzNiedersachsen(
				hospitalisierungStream);

			ResourceBundle strings = ResourceBundle.getBundle("strings");

			StringBuilder output = new StringBuilder("""
				<html>
				<head>
				  <title>Corona-Overview</title><meta charset="UTF-8">
				  <link rel="icon" href="favicon.ico">
				  <script type="text/javascript">function details(classname) {
				    var el = document.getElementsByClassName(classname + "m");
				    for (var i = 0; i < el.length; i++) {
				         el[i].style.display = "none";
				    }
				    el = document.getElementsByClassName(classname);
				    for (var i = 0; i < el.length; i++) {
				      el[i].style.display = "inherit";
				    }
				  }</script>
				</head>
				<body style="padding:30px;padding-left:60px">
				""");

			List<LocalDate> datesToDisplay = new ArrayList<>();
			for (int i = 0; i < DAYS; i++) {
				datesToDisplay.add(LocalDate.now().minusDays(i));
			}

			for (String location : locations) {
				String locationClass = location.replaceAll("[^a-zA-Z]", "").toUpperCase();

				output.append("<h3>")
					.append(location)
					.append("</h3>\n<table style=\"padding-left:20px\" onclick=\"details('")
					.append(locationClass)
					.append("')\">");

				AtomicInteger line = new AtomicInteger();

				Map<LocalDate, Double> fallinzidenz = fallinzidenzProOrt.get(location);

				for (LocalDate day : datesToDisplay) {
					output.append("<tr")
						.append(line.get() > 0 ? " class=\"" + locationClass + "\" style=\"display:none\""
							: " style=\"display:inherit\"")
						.append(">")
						.append("<td style=\"padding-right:25px;text-align:right\">")
						.append(line.get() > 0 ? "" : "<b>")
						.append("<span title=\"Fallinzidenz (pro Landkreis bzw. Stadt berechnet)\">")
						.append(fallinzidenz.get(day) == null
							? "?"
							: INCIDENCE_NUMBER_FORMAT.format(fallinzidenz.get(day)))
						.append("</span>")
						.append(line.get() > 0 ? "" : "</b>")
						.append("</td><td style=\"padding-right:25px;text-align:right\">")
						.append("<span title=\"Hospitalisierungsinzidenz (pro Bundesland berechnet)\">H: ")
						.append(hospitalisierungNiedersachsen.get(day) == null
							? "?"
							: INCIDENCE_NUMBER_FORMAT.format(hospitalisierungNiedersachsen.get(day)))
						.append("</span>")
						.append("</td><td style=\"color:#606060\"><small>")
						.append(DATE_FORMAT.format(day))
						.append("</small></td></tr>\n");
					if (line.get() == 0) {
						output.append("<tr class=\"")
							.append(locationClass)
							.append("m\"><td colspan=\"2\" style=\"text-align:center\">...</td></tr>\n");
					}
					line.incrementAndGet();
				}

				output.append("</table>\n");
			}
			output
				.append("<br/><br/><br/><small><i>")
				.append(strings.getString("data_from"))
				.append(" ")
				.append(DATE_TIME_FORMAT.format(LocalDateTime.now()))
				.append(" ")
				.append(strings.getString("oclock"))
				.append("<br/>\n")
				.append(strings.getString("data_source"))
				.append(
					"""
						<a href="https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/nCoV_node.html" target="_blank">Robert-Koch-Institut</a>
						</i></small>
						</body>
						</html>
						""");

			System.out.println(output.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Map<LocalDate, Double> loadHospitalisierungsinzidenzNiedersachsen(final InputStream inputStream) {
		Map<LocalDate, Double> result = new HashMap<>();

		// Datum,Bundesland,Bundesland_Id,Altersgruppe,7T_Hospitalisierung_Faelle,7T_Hospitalisierung_Inzidenz
		try (CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(inputStream)).withSkipLines(1).build()) {
			String[] line;
			while ((line = csvReader.readNext()) != null) {
				if (line.length >= 6
					&& line[1].equalsIgnoreCase("Niedersachsen")
					&& line[3].equalsIgnoreCase("00+")) {
					result.put(LocalDate.parse(line[0], RKI_CSV_INPUT_DATE_FORMAT), Double.valueOf(line[5]));
				}
			}
		} catch (IOException | CsvValidationException e) {
			throw new RuntimeException("problem while loading hostpitalization incidence", e);
		}

		return result;
	}

	private Map<String, Map<LocalDate, Double>> loadFallinzidenzProOrt(final InputStream inputStream) {
		Map<String, Map<LocalDate, Double>> result = new HashMap<>();

		LocalDate today = LocalDate.now();

		try (XSSFWorkbook wb = new XSSFWorkbook(inputStream)) {
			XSSFSheet sheet = null;

			Iterator<Sheet> sheetIterator = wb.sheetIterator();
			while (sheet == null && sheetIterator.hasNext()) {
				Sheet toTest = sheetIterator.next();
				if (toTest.getSheetName().contains("LK_7-Tage-Inzidenz")
					&& toTest instanceof XSSFSheet s) {
					sheet = s;
				}
			}

			if (sheet != null) {
				Map<Integer, LocalDate> colToDate = new HashMap<>();
				for (Row row : sheet) {
					if (row.getCell(1) == null || row.getCell(2) == null) {
						continue;
					}
					String cell1 = row.getCell(1).getStringCellValue();
					String cell2 = row.getCell(2).getCellType() == CellType.STRING
						? row.getCell(2).getStringCellValue()
						: (row.getCell(2).getCellType() == CellType.NUMERIC
							? ID_NUMBER_FORMAT.format(row.getCell(2).getNumericCellValue())
							: "");
					if (cell1.equals("LK") && cell2.equals("LKNR")) {
						for (int col = 3; col < row.getLastCellNum(); col++) {
							Cell cell = row.getCell(col);
							if (cell != null && cell.getCellType() == CellType.STRING) {
								colToDate.put(col,
									LocalDate.from(RKI_EXCEL_INPUT_DATE_FORMAT.parse(cell.getStringCellValue())));
							} else if (cell != null && cell.getCellType() == CellType.NUMERIC) {
								LocalDate date = cell.getDateCellValue()
									.toInstant()
									.atZone(ZoneId.systemDefault())
									.toLocalDate();
								colToDate.put(col, date);
							}
						}
					} else if (locations.stream().anyMatch(cell1::contains)) {
						String landkreis = locations.stream().filter(cell1::contains).findAny().orElseThrow();
						Map<LocalDate, Double> values = new HashMap<>();
						for (int col = 3; col < row.getLastCellNum(); col++) {
							Cell cell = row.getCell(col);
							if (cell != null) {
								Double value = cell.getNumericCellValue();
								values.put(colToDate.get(col), value);
							}
						}
						if (result.containsKey(landkreis)) {
							throw new IllegalStateException("data already present for: " + landkreis);
						}
						result.put(landkreis, values);
					}
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		// add today's value from web service in case it's missing in XLSX file
		try {
			JsonElement root = loadDataForTodayFromWebService();

			for (String landkreis : locations) {
				result.computeIfAbsent(landkreis, lk -> new HashMap<>());
				if (!result.get(landkreis).containsKey(today)) {
					result.get(landkreis).put(today, getValueForToday(root, landkreis));
				}
			}
		} catch (Exception e) {
			System.err.println("<!-- error while loading data from web service: " + e + " -->");
		}

		return result;
	}

	private JsonElement loadDataForTodayFromWebService() throws IOException {
		String url = "https://services7.arcgis.com/mOBPykOjAyBO2ZKk/arcgis/rest/services/RKI_Landkreisdaten/FeatureServer/0/query?where=1%3D1&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&resultType=none&distance=0.0&units=esriSRUnit_Meter&returnGeodetic=false&outFields=GEN%2CBEZ%2Ccases7_per_100k&returnGeometry=false&returnCentroid=false&featureEncoding=esriDefault&multipatchOption=xyFootprint&maxAllowableOffset=&geometryPrecision=&outSR=&datumTransformation=&applyVCSProjection=false&returnIdsOnly=false&returnUniqueIdsOnly=false&returnCountOnly=false&returnExtentOnly=false&returnQueryGeometry=false&returnDistinctValues=false&cacheHint=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&having=&resultOffset=&resultRecordCount=&returnZ=false&returnM=false&returnExceededLimitFeatures=true&quantizationParameters=&sqlFormat=none&f=pjson&token=";
		URLConnection request = new URL(url).openConnection();
		request.connect();
		return JsonParser.parseReader(new InputStreamReader((InputStream) request.getContent()));
	}

	private Double getValueForToday(final JsonElement root, final String landkreis) {
		JsonArray incidences = root.getAsJsonObject().get("features").getAsJsonArray();
		for (JsonElement incidence : incidences) {
			JsonObject jsonObject = incidence.getAsJsonObject();
			JsonObject attributes = jsonObject.get("attributes").getAsJsonObject();
			String name = attributes.get("GEN").getAsString();
			if (name != null && name.toLowerCase().contains(landkreis.toLowerCase())) {
				return attributes.get("cases7_per_100k").getAsDouble();
			}
		}
		return null;
	}

}
