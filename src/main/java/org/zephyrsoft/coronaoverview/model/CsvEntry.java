package org.zephyrsoft.coronaoverview.model;

import java.time.LocalDate;

import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvDate;

public class CsvEntry {
	@CsvBindByPosition(position = 0)
	@CsvDate("dd.MM.yyyy")
	private LocalDate meldedatum;
	@CsvBindByPosition(position = 3)
	private String landkreis;
	@CsvBindByPosition(position = 6)
	private Double siebenTagesInzidenzPro100000Einwohner;

	public LocalDate getMeldedatum() {
		return meldedatum;
	}

	public String getLandkreis() {
		return landkreis;
	}

	public Double getSiebenTagesInzidenzPro100000Einwohner() {
		return siebenTagesInzidenzPro100000Einwohner;
	}

}
