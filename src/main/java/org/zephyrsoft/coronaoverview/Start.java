package org.zephyrsoft.coronaoverview;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

import org.zephyrsoft.coronaoverview.model.CsvEntry;

import com.google.common.collect.MoreCollectors;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

public class Start {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy");
	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy, HH:mm");
	private static final NumberFormat NUMBER_FORMAT = DecimalFormat.getNumberInstance();
	static {
		NUMBER_FORMAT.setMinimumFractionDigits(1);
		NUMBER_FORMAT.setMaximumFractionDigits(1);
	}

	public static void main(String[] args) {
		new Start(List.of(args));
	}

	private Start(List<String> locations) {
		if (locations.isEmpty()) {
			System.err.println("no locations given");
		} else {
			handle(locations);
		}
	}

	private void handle(List<String> locations) {
		try (
			InputStream is = new URL("https://www.apps.nlga.niedersachsen.de/corona/download.php?csv_tag_region")
				.openStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

			CsvToBean<CsvEntry> csvReader = new CsvToBeanBuilder<CsvEntry>(reader)
				.withSkipLines(1)
				.withSeparator(';')
				.withQuoteChar('"')
				.withType(CsvEntry.class)
				.build();

			Map<String, Map<LocalDate, Double>> result = csvReader.stream()
				.filter(e -> locations.stream().anyMatch(loc -> e.getLandkreis().startsWith(loc)))
				.collect(groupingBy(CsvEntry::getLandkreis,
					groupingBy(CsvEntry::getMeldedatum,
						mapping(CsvEntry::getSiebenTagesInzidenzPro100000Einwohner, MoreCollectors.onlyElement()))));

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
			for (String location : locations) {
				result.entrySet().stream()
					.filter(e -> e.getKey().startsWith(location))
					.forEach(perLocation -> {
						output.append("<h3>")
							.append(perLocation.getKey())
							.append("</h3>\n<table style=\"padding-left:20px\">\n");

						String locationClass=location.replaceAll("[^a-zA-Z]", "").toUpperCase();
						AtomicInteger line = new AtomicInteger();
						perLocation.getValue().entrySet().stream()
							.sorted(Comparator.comparing((Map.Entry<LocalDate, Double> e) -> e.getKey()).reversed())
							.limit(5)
							.forEach(perDay -> {
								output.append("<tr")
									.append(line.get() > 0 ? " class=\"" + locationClass + "\" style=\"display:none\"" : " style=\"display:inherit\"")
									.append("><td align=\"right\">")
									.append(line.get() > 0 ? "" : "<b>")
									.append(NUMBER_FORMAT.format(perDay.getValue()))
									.append(line.get() > 0 ? "" : "</b>")
									.append("</td><td style=\"padding-left:10px;color:#606060\"><small>(")
									.append(DATE_FORMAT.format(perDay.getKey()))
									.append(")</small></td></tr>\n");
								if (line.get() == 0) {
									output.append("<tr class=\"")
										.append(locationClass)
										.append("m\" onclick=\"details('")
										.append(locationClass)
										.append("')\"><td colspan=\"2\" style=\"text-align:center\">...</td></tr>\n");
								}
								line.incrementAndGet();
							});

						output.append("</table>\n");
					});
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
				.append(" <a href=\"https://www.niedersachsen.de/Coronavirus/aktuelle_lage_in_niedersachsen/\" target=\"_blank\">niedersachsen.de</a></i></small></body>\n</html>");

			System.out.println(output.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
