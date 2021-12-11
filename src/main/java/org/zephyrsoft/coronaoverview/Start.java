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
import java.util.Locale;
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
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

public class Start {

	private static final int DAYS = 10;

	private static final DateTimeFormatter DATE_FORMAT_DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final DateTimeFormatter DATE_FORMAT_YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
		try (InputStream rkiFallinzidenzStream = new URL(
			"https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Daten/Fallzahlen_Inzidenz_aktualisiert.xlsx?__blob=publicationFile")
				.openStream();
			InputStream rkiHospitalisierungStream = new URL(
				"https://raw.githubusercontent.com/robert-koch-institut/COVID-19-Hospitalisierungen_in_Deutschland/master/Aktuell_Deutschland_COVID-19-Hospitalisierungen.csv")
					.openStream();
			InputStream ndsHospitalisierungStream = new URL(
				"https://www.ms.niedersachsen.de/download/177306/CSV-Tabelle.csv")
					.openStream()) {

			Map<String, Map<LocalDate, Double>> rkiFallinzidenzProOrt = loadRkiFallinzidenzProOrt(
				rkiFallinzidenzStream);
			Map<LocalDate, Double> rkiHospitalisierungNiedersachsen = loadRkiHospitalisierungsinzidenzNiedersachsen(
				rkiHospitalisierungStream);
			Map<LocalDate, Indicators> ndsData = loadNdsData(ndsHospitalisierungStream);

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

				Map<LocalDate, Double> fallinzidenz = rkiFallinzidenzProOrt.get(location);

				for (LocalDate day : datesToDisplay) {
					output.append("<tr")
						.append(line.get() > 0 ? " class=\"" + locationClass + "\" style=\"display:none\""
							: " style=\"display:inherit\"")
						.append(">")
						.append("<td style=\"padding-right:25px;text-align:right\">")
						.append(line.get() > 0 ? "" : "<b>")
						.append("<span title=\"")
						.append(strings.getString("case_incidence"))
						.append("\">")
						.append(fallinzidenz.get(day) == null
							? "?"
							: INCIDENCE_NUMBER_FORMAT.format(fallinzidenz.get(day)))
						.append("</span>")
						.append(line.get() > 0 ? "" : "</b>")
						.append("</td><td style=\"padding-right:25px;text-align:right\">")
						.append(line.get() > 0 ? "" : "<b>")
						.append("<span title=\"")
						.append(strings.getString("hospitalization_incidence_nds"))
						.append("\">H<small><sub>NDS</sub></small>: ")
						.append(ndsData.get(day) == null
							? "?"
							: INCIDENCE_NUMBER_FORMAT.format(ndsData.get(day).hospitalisationIncidence()))
						.append("</span>")
						.append(line.get() > 0 ? "" : "</b>")
						.append("</td><td style=\"padding-right:25px;text-align:right; color:#A0A0A0\">")
						.append(line.get() > 0 ? "" : "<b>")
						.append("<span title=\"")
						.append(strings.getString("hospitalization_incidence_rki"))
						.append("\">H<small><sub>RKI</sub></small>: ")
						.append(rkiHospitalisierungNiedersachsen.get(day) == null
							? "?"
							: INCIDENCE_NUMBER_FORMAT.format(rkiHospitalisierungNiedersachsen.get(day)))
						.append("</span>")
						.append(line.get() > 0 ? "" : "</b>")
						.append("</td><td style=\"padding-right:25px;text-align:right\">")
						.append(line.get() > 0 ? "" : "<b>")
						.append("<span title=\"")
						.append(strings.getString("intensive_care_beds_percentage_nds"))
						.append("\">K: ")
						.append(ndsData.get(day) == null
							? "?"
							: INCIDENCE_NUMBER_FORMAT.format(ndsData.get(day).intensiveCaseBedsPercentage()) + " %")
						.append("</span>")
						.append(line.get() > 0 ? "" : "</b>")
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
				.append(" ")
				.append(
					"""
						<a href="https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/nCoV_node.html" target="_blank">Robert-Koch-Institut</a> /
						<a href="https://www.ms.niedersachsen.de/startseite/gesundheit_pflege/gesundheit/krankenhauser/krankenhausplanung/krankenhauser_nach_21/krankenhauser-nach-21-krankenhausfinanzierungsgesetz-khg-195705.html" target="_blank">Land Niedersachsen</a>
						</i></small>
						</body>
						</html>
						""");

			System.out.println(output.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Map<LocalDate, Indicators> loadNdsData(final InputStream inputStream) {
		Map<LocalDate, Indicators> result = new HashMap<>();

		CSVParser parser = new CSVParserBuilder().withSeparator(';').build();

		// Daten Stand, jeweils 09:00 Uhr;Inzidenz;Hospitalisierung;Intensivbetten in %
		try (CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(inputStream))
			.withCSVParser(parser).withSkipLines(1).build()) {
			NumberFormat format = NumberFormat.getInstance(Locale.GERMAN);
			String[] line;
			while ((line = csvReader.readNext()) != null) {
				if (line.length == 1 && line[0].contains("\t")) {
					line = line[0].split("\t+");
				}
				Indicators ind = new Indicators(format.parse(line[1]).doubleValue(),
					format.parse(line[2]).doubleValue(),
					format.parse(line[3]).doubleValue());
				result.put(LocalDate.parse(line[0], DATE_FORMAT_DMY), ind);
			}
		} catch (Exception e) {
			throw new RuntimeException("problem while loading lower saxony data", e);
		}

		return result;
	}

	private Map<LocalDate, Double> loadRkiHospitalisierungsinzidenzNiedersachsen(final InputStream inputStream) {
		Map<LocalDate, Double> result = new HashMap<>();

		// Datum,Bundesland,Bundesland_Id,Altersgruppe,7T_Hospitalisierung_Faelle,7T_Hospitalisierung_Inzidenz
		try (CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(inputStream)).withSkipLines(1).build()) {
			String[] line;
			while ((line = csvReader.readNext()) != null) {
				if (line.length >= 6
					&& line[1].trim().equalsIgnoreCase("Niedersachsen")
					&& line[3].trim().equalsIgnoreCase("00+")) {
					result.put(LocalDate.parse(line[0], DATE_FORMAT_YMD), Double.valueOf(line[5].trim()));
				}
			}
		} catch (IOException | CsvValidationException e) {
			throw new RuntimeException("problem while loading hostpitalization incidence", e);
		}

		return result;
	}

	private Map<String, Map<LocalDate, Double>> loadRkiFallinzidenzProOrt(final InputStream inputStream) {
		Map<String, Map<LocalDate, Double>> result = new HashMap<>();

		LocalDate today = LocalDate.now();

		try (XSSFWorkbook wb = new XSSFWorkbook(inputStream)) {
			XSSFSheet sheet = null;

			Iterator<Sheet> sheetIterator = wb.sheetIterator();
			while (sheet == null && sheetIterator.hasNext()) {
				Sheet toTest = sheetIterator.next();
				if (toTest.getSheetName().contains("LK_7-Tage-Inzidenz-aktualisiert")
					&& toTest instanceof XSSFSheet s) {
					sheet = s;
				}
			}

			if (sheet != null) {
				Map<Integer, LocalDate> colToDate = new HashMap<>();
				for (Row row : sheet) {
					if (row.getCell(0) == null || row.getCell(1) == null) {
						continue;
					}
					String cellLkNr = row.getCell(0).getCellType() == CellType.STRING
						? row.getCell(0).getStringCellValue()
						: (row.getCell(0).getCellType() == CellType.NUMERIC
							? ID_NUMBER_FORMAT.format(row.getCell(0).getNumericCellValue())
							: "");
					String cellLkName = row.getCell(1).getStringCellValue();
					if (cellLkNr.equals("IdMeldeLandkreis") && cellLkName.equals("MeldeLandkreis")) {
						for (int col = 2; col < row.getLastCellNum(); col++) {
							Cell cell = row.getCell(col);
							if (cell != null && cell.getCellType() == CellType.STRING) {
								colToDate.put(col,
									LocalDate.from(DATE_FORMAT_DMY.parse(cell.getStringCellValue())));
							} else if (cell != null && cell.getCellType() == CellType.NUMERIC) {
								LocalDate date = cell.getDateCellValue()
									.toInstant()
									.atZone(ZoneId.systemDefault())
									.toLocalDate();
								colToDate.put(col, date);
							}
						}
					} else if (locations.stream().anyMatch(cellLkName::contains)) {
						String landkreis = locations.stream().filter(cellLkName::contains).findAny().orElseThrow();
						Map<LocalDate, Double> values = new HashMap<>();
						for (int col = 2; col < row.getLastCellNum(); col++) {
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
