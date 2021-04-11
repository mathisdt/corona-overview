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
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.zephyrsoft.coronaoverview.model.CsvEntry;

import com.google.common.collect.MoreCollectors;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

public class Start {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy");
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

			StringBuilder output = new StringBuilder("<html><head><title>Corona-Overview</title></head><body>");
			for (String location : locations) {
				result.entrySet().stream()
					.filter(e -> e.getKey().startsWith(location))
					.forEach(perLocation -> {
						output.append("<h3>")
							.append(perLocation.getKey())
							.append("</h3>\n<table>\n");

						AtomicInteger line = new AtomicInteger();
						perLocation.getValue().entrySet().stream()
							.sorted(Comparator.comparing((Map.Entry<LocalDate, Double> e) -> e.getKey()).reversed())
							.limit(5)
							.forEach(perDay -> {
								output.append("<tr><td align=\"right\">")
									.append(line.get() > 0 ? "" : "<b>")
									.append(NUMBER_FORMAT.format(perDay.getValue()))
									.append(line.get() > 0 ? "" : "</b>")
									.append("</td><td style=\"padding-left:10px\">(")
									.append(DATE_FORMAT.format(perDay.getKey()))
									.append(")</td></tr>\n");
								line.incrementAndGet();
							});

						output.append("</table>\n");
					});
			}
			output.append("</body></html>");

			System.out.println(output.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
