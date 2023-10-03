package jss.cpuspec;

import java.util.ArrayList;

public class MacroData {
	String name;
	ArrayList<String> params;
	String code;
	
	public MacroData(String name) {
		this.name=name;
		this.params=new ArrayList<>(10);
		this.code=null;
	}
}
