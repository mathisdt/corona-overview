package org.zephyrsoft.coronaoverview;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.zephyrsoft.coronaoverview.model.CsvEntry;

import com.google.common.collect.MoreCollectors;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

public class Start {

	private static final DateTimeFormatter RKI_INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy");
	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy, HH:mm");
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance();

	static {
		NUMBER_FORMAT.setMinimumFractionDigits(1);
		NUMBER_FORMAT.setMaximumFractionDigits(1);
	}

	private List<String> locations;

	public static void main(String[] args) {
		new Start(List.of(args));
	}

	private Start(List<String> locations) {
		this.locations = locations;
		if (locations.isEmpty()) {
			System.err.println("no locations given");
		} else {
			doWork();
		}
	}

	private void doWork() {
		try (
			InputStream niedersachsenDeStream = new URL(
				"https://www.apps.nlga.niedersachsen.de/corona/download.php?csv_tag_region")
					.openStream();
			BufferedReader niedersachsenDeReader = new BufferedReader(new InputStreamReader(niedersachsenDeStream));
			InputStream rkiDeStream = new URL(
				"https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/Daten/Fallzahlen_Kum_Tab.xlsx?__blob=publicationFile")
					.openStream()) {

			Map<String, Map<LocalDate, Double>> niedersachsenDe = loadFromNiedersachsenDe(niedersachsenDeReader);
			Map<String, Map<LocalDate, Double>> rkiDe = loadFromRkiDe(rkiDeStream);

			ResourceBundle strings = ResourceBundle.getBundle("strings");

			StringBuilder output = new StringBuilder(
				"<html>\n<head><title>Corona-Overview</title><meta charset=\"UTF-8\"><link rel=\"icon\" href=\"favicon.ico\">"
					+ "<script type=\"text/javascript\">function details(classname) {\n"
					+ " var el = document.getElementsByClassName(classname + \"m\");\n"
					+ " for (var i = 0; i < el.length; i++) {\n"
					+ "  el[i].style.display = \"none\";\n"
					+ " }\n"
					+ " el = document.getElementsByClassName(classname);\n"
					+ " for (var i = 0; i < el.length; i++) {\n"
					+ "  el[i].style.display = \"inherit\";\n"
					+ " }\n"
					+ "}</script>"
					+ "</head>\n<body style=\"padding:30px;padding-left:60px\">\n");

			List<LocalDate> datesToDisplay = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
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

				Map<LocalDate, Double> valuesNiedersachsenDe = niedersachsenDe.get(location);
				Map<LocalDate, Double> valuesRkiDe = rkiDe.get(location);

				for (LocalDate day : datesToDisplay) {
					output.append("<tr")
						.append(line.get() > 0 ? " class=\"" + locationClass + "\" style=\"display:none\""
							: " style=\"display:inherit\"")
						.append(">")
						.append("<td style=\"padding-right:10px;text-align:right\">")
						.append(line.get() > 0 ? "" : "<b>")
						.append(valuesNiedersachsenDe.get(day) == null
							? "?"
							: NUMBER_FORMAT.format(valuesNiedersachsenDe.get(day)))
						.append(line.get() > 0 ? "" : "</b>")
						.append("<small style=\"color:#606060\"><sub>NDS</sub></small></td>")
						.append("<td style=\"padding-right:10px;text-align:right\">")
						.append(line.get() > 0 ? "" : "<b>")
						.append(valuesRkiDe.get(day) == null
							? "?"
							: NUMBER_FORMAT.format(valuesRkiDe.get(day)))
						.append(line.get() > 0 ? "" : "</b>")
						.append("<small style=\"color:#606060\"><sub>RKI</sub></small></td>")
						.append("<td style=\"color:#606060\"><small>")
						.append(DATE_FORMAT.format(day))
						.append("</small></td>")
						.append("</tr>\n");
					if (line.get() == 0) {
						output.append("<tr class=\"")
							.append(locationClass)
							.append("m\"><td colspan=\"3\" style=\"text-align:center\">...</td></tr>\n");
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
				.append(strings.getString("data_sources"))
				.append(
					" <a href=\"https://www.niedersachsen.de/Coronavirus/aktuelle_lage_in_niedersachsen/\" target=\"_blank\">Landesgesundheitsamt Niedersachsen</a> / <a href=\"https://www.rki.de/DE/Content/InfAZ/N/Neuartiges_Coronavirus/nCoV_node.html\" target=\"_blank\">Robert-Koch-Institut</a> ")
				.append(strings.getString("update_frequency"))
				.append("</i></small></body>\n</html>");

			System.out.println(output.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Map<String, Map<LocalDate, Double>> loadFromNiedersachsenDe(BufferedReader reader) {
		CsvToBean<CsvEntry> csvReader = new CsvToBeanBuilder<CsvEntry>(reader)
			.withSkipLines(1)
			.withSeparator(';')
			.withQuoteChar('"')
			.withType(CsvEntry.class)
			.build();

		return csvReader.stream()
			.filter(e -> locations.stream().filter(loc -> e.getLandkreis().contains(loc)).count() == 1)
			.map(e -> new CsvEntry(e.getMeldedatum(),
				locations.stream().filter(loc -> e.getLandkreis().contains(loc)).collect(MoreCollectors.onlyElement()),
				e.getSiebenTagesInzidenzPro100000Einwohner()))
			.collect(groupingBy(CsvEntry::getLandkreis,
				groupingBy(CsvEntry::getMeldedatum,
					mapping(CsvEntry::getSiebenTagesInzidenzPro100000Einwohner, MoreCollectors.onlyElement()))));
	}

	private Map<String, Map<LocalDate, Double>> loadFromRkiDe(InputStream rkiDeStream) {
		Map<String, Map<LocalDate, Double>> result = new HashMap<>();

		try (XSSFWorkbook wb = new XSSFWorkbook(rkiDeStream)) {
			XSSFSheet sheet = wb.getSheet("LK_7-Tage-Inzidenz");

			Map<Integer, LocalDate> colToDate = new HashMap<>();

			for (Row row : sheet) {
				if (row.getCell(1) == null || row.getCell(2) == null) {
					continue;
				}
				String cell1 = row.getCell(1).getStringCellValue();
				String cell2 = row.getCell(2).getCellType() == CellType.STRING
					? row.getCell(2).getStringCellValue()
					: "";
				if (cell1.equals("LK") && cell2.equals("LKNR")) {
					for (int col = 3; col < row.getLastCellNum(); col++) {
						Cell cell = row.getCell(col);
						if (cell != null && cell.getCellType() == CellType.STRING) {
							colToDate.put(col,
								LocalDate.from(RKI_INPUT_DATE_FORMAT.parse(cell.getStringCellValue())));
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
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		return result;
	}

}
