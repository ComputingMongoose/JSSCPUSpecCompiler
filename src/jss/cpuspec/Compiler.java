package jss.cpuspec;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Compiler {

	/* Macro notation inspired from: https://jsesoft.sourceforge.net/
	 * That implementation fails for a large number of macro expansions.
	 */
	
	
	static final int STATE_0=0;
	static final int STATE_READ_MAP=1;
	static final int STATE_READ_INSTR_START=2;
	static final int STATE_READ_INSTR_CODE=3;
	
	static class MapElement {
		String name;
		int code;
		String codeString;
		
		MapElement(String name, String codeString){
			this.name=name;
			this.codeString=codeString;
			this.code=Integer.parseInt(codeString, 2);
		}
	}
	
	static class MapData {
		ArrayList<MapElement> list;
		
		MapData(){
			list=new ArrayList<>(100);
		}
	}
	
	@SuppressWarnings("serial")
	static class CompilerException extends Exception {

		public CompilerException(String string) {
			super(string);
		}
		
	}
	
	static class OpcodeData {
		String[] opcode;
		HashMap<String,MapElement> values;
		
		OpcodeData(){
			opcode=null;
			values=new HashMap<>(100);
		}
		
		OpcodeData(OpcodeData op){
			opcode=new String[op.opcode.length];
			for(int i=0;i<op.opcode.length;i++)opcode[i]=op.opcode[i];
			values=new HashMap<>(op.values.size()+1);
			for(Map.Entry<String, MapElement> entry:op.values.entrySet()) {
				values.put(entry.getKey(), entry.getValue());
			}
		}
	}
	
	static class CodeData {
		String code;
		String label;
		
		public CodeData(String code, String label) {
			this.code=code;
			this.label=label;
		}
	}
	
	
	static HashMap<String,Object> opcodeTable;
	static HashMap<String,MacroData> macros;
	
	public static boolean expandOpcodes(ArrayList<OpcodeData> opcodes,HashMap<String,MapData> maps) throws CompilerException {
		boolean changes=false;
		
		ArrayList<Integer> deleteElements=new ArrayList<>(100);
		int len=opcodes.size();
		for(int i=0;i<len;i++) {
			OpcodeData op=opcodes.get(i);
			for(int j=0;j<op.opcode.length;j++) {
				boolean stop_current_opcode_processing=false;
				String []current=op.opcode[j].split("[|]");
				if(current.length>1) {
					changes=true;
					deleteElements.add(Integer.valueOf(i));
					
					String currentNewOpcode="";
					boolean added=false;
					for(int k=0;k<current.length;k++) {
						String c=current[k];
						if(c.charAt(0)=='0' || c.charAt(0)=='1')currentNewOpcode+=c;
						else {
							if(!maps.containsKey(c))throw new CompilerException("Invalid map ["+c+"]");
							for(MapElement value:maps.get(c).list) {
								// Form new opcode
								String copcode=currentNewOpcode+value.codeString;
								
								// Add remaining opcode parts
								for(int k1=k+1;k1<current.length;k1++) {
									if(current[k1].charAt(0)=='0' || current[k1].charAt(0)=='1')copcode+=current[k1];
									else copcode+="|"+current[k1];
								}
								
								OpcodeData ndata=new OpcodeData(op);
								ndata.opcode[j]=copcode;
								ndata.values.put(c,value);
								opcodes.add(ndata);
								added=true;
							}
							stop_current_opcode_processing=true;
							break; // from k loop
						}
					}
					if(!added) {
						System.out.println("ADDING: "+currentNewOpcode);
						OpcodeData ndata=new OpcodeData(op);
						ndata.opcode[j]=currentNewOpcode;
						opcodes.add(ndata);
						stop_current_opcode_processing=true;
					}
					
				}
				if(stop_current_opcode_processing)break;
			}
		}
		
		Collections.sort(deleteElements, Collections.reverseOrder());
		for(Integer d:deleteElements) {
			opcodes.remove(d.intValue());
		}
		
		return changes;
	}
	
	public static void printOpcodes(ArrayList<OpcodeData> opcodes) {
		System.out.println("Opcodes:");
		for(OpcodeData d:opcodes) {
			System.out.print(String.join(" ", d.opcode));
			System.out.print(" [");
			for(Map.Entry<String, MapElement> en:d.values.entrySet()) {
				System.out.print(en.getKey()+"="+en.getValue().code+":"+en.getValue().name+",");
			}
			System.out.println("]");
		}
		System.out.println();
	}
	
	@SuppressWarnings("unchecked")
	public static void printOpcodeTable(Object ctable, int opcodeByte, String prefix, PrintStream outMain, PrintStream outMethods) {
		if(ctable instanceof CodeData) {
			CodeData cd=(CodeData)ctable;
			outMain.println(" //"+cd.label);
			outMain.println(cd.code); // AICI SE POATE PUNE DISSASEMBLER
			//if(opcodeByte>2)outMethods.println("break;\n");
			//else outMethods.println("}\n");
			outMain.println("}");
		}else if(ctable instanceof HashMap<?,?>) {
			HashMap<String,Object> tab=(HashMap<String,Object>)ctable;
			if(opcodeByte>1) {
				outMain.println("#READ_OPCODE_"+opcodeByte+"();");
			}
			outMain.println("switch(opcodeByte"+opcodeByte+"){");
			boolean hasDefault=false;
			List<String> keys=new ArrayList<String>(tab.keySet());
			Collections.sort(keys);
			for(String key:keys) {
				String nprefix=prefix;
				if(key.contentEquals("default")) {
					outMain.print("default:");
					nprefix+="_default";
					outMain.println("opcode_"+nprefix+"();");
					outMain.println("break;");
					hasDefault=true;
				}else { 
					nprefix+="_"+String.format("0x%02X", Integer.parseInt(key,2));
					outMain.println("case "+String.format("0x%02X", Integer.parseInt(key,2))+":");
					outMain.println("opcode_"+nprefix+"();");
					outMain.println("break;");
				}
				outMethods.println("private void opcode_"+nprefix+"()  throws MemoryAccessException, ControlBusUnknownSignalException, CPUInvalidOpcodeException {");
				ByteArrayOutputStream baosMethods=new ByteArrayOutputStream();
				PrintStream outStreamMethods=new PrintStream(baosMethods);
				printOpcodeTable(tab.get(key),opcodeByte+1,nprefix,outMethods,outStreamMethods);
				outStreamMethods.flush();outStreamMethods.close();
				outMethods.println(baosMethods.toString());
			}
			if(!hasDefault) {
				//if(opcodeByte>1)outMethods.println("default: throw new CPUInvalidOpcodeException(new long[] {opcodeByte1});");
				//else 
					outMain.println("default: throw new CPUInvalidOpcodeException(new long[] {opcodeByte1});");
			}
			if(opcodeByte>1)outMain.println("}\n}");
			else 
				outMain.println("}");
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public static void processInstruction(String label,String[]opcodeDef, String instrCode,HashMap<String,MapData> maps) throws CompilerException {
		ArrayList<OpcodeData> opcodes=new ArrayList<>(100);
		OpcodeData d=new OpcodeData();
		opcodes.add(d);
		d.opcode=new String[opcodeDef.length];
		for(int i=0;i<opcodeDef.length;i++)d.opcode[i]=opcodeDef[i];
		
		System.out.println("INITIAL");
		printOpcodes(opcodes);
		while(expandOpcodes(opcodes,maps)); //printOpcodes(opcodes);
		System.out.println("FINAL");
		printOpcodes(opcodes);
		
		// Make opcode table
		for(OpcodeData op:opcodes) {
			String currentCode=instrCode;
			String currentLabel=label;
			for(Map.Entry<String, MapElement> vmap:op.values.entrySet()) {
				String k=vmap.getKey();
				String v=vmap.getValue().name;
				currentCode=currentCode.replaceAll("[$]"+k+"[$]", v);
				
				currentLabel+=" "+k+"="+v;
			}
			
			HashMap<String,Object> ctable=opcodeTable;
			for(int i=0;i<op.opcode.length;i++) {
				String cop=op.opcode[i];
				if(cop.length()!=8) {
					throw new CompilerException("Invalid opcode ["+cop+"]");
				}
				System.out.println(currentLabel+" opcode="+cop);
				if(i==op.opcode.length-1) { // last opcode
					if(!ctable.containsKey(cop)) {
						ctable.put(cop, new CodeData(currentCode, currentLabel));
					}else {
						if(ctable.get(cop) instanceof CodeData)
							throw new CompilerException("Opcode clash: "+currentLabel+" opcode="+cop);
						ctable=(HashMap<String,Object>)ctable.get(cop);
						if(ctable.containsKey("default")) {
							throw new CompilerException("Opcode clash (2): "+currentLabel);
						}
						ctable.put("default", new CodeData(currentCode, currentLabel));
					}
				}else {
					if(!ctable.containsKey(cop)) {
						ctable.put(cop,new HashMap<String,Object>(100));
					}else {
						if(ctable.get(cop) instanceof CodeData) {
							HashMap<String,Object> nh=new HashMap<String,Object>(100);
							nh.put("default", ctable.get(cop));
							ctable.put(cop, nh);
						} // else already exists a hashmap
					}
					ctable=(HashMap<String,Object>)ctable.get(cop);
				}
			}
		}
		
	}
	
	public static void main(String[] args) throws IOException, CompilerException, MacroExpansionException {

		if(args.length!=2) {
			System.out.println("Usage:");
			System.out.println("   Compiler <SPEC> <OUTPUT>");
			System.exit(-1);
			return ;
		}
		
		HashMap<String,MapData> maps=new HashMap<>(20);
		
		int state=STATE_0;
		String currentMap=null;
		
		String[]opcode=null;
		StringBuffer instrCode=new StringBuffer();
		String instrLabel=null;
		opcodeTable=new HashMap<>(10000);
		macros=new HashMap<>(1000);
		
		ByteArrayOutputStream baosMain=new ByteArrayOutputStream();
		PrintStream outStreamMain=new PrintStream(baosMain);
		ByteArrayOutputStream baosMethods=new ByteArrayOutputStream();
		PrintStream outStreamMethods=new PrintStream(baosMethods);
		
		BufferedReader inReader=new BufferedReader(new InputStreamReader(new FileInputStream(args[0]),Charset.forName("UTF-8")));
		for(String line=inReader.readLine();line!=null;line=inReader.readLine()) {
			switch(state) {
			case STATE_0:
				if(line.startsWith("MAP ")) {
					state=STATE_READ_MAP;
					String[] data=line.split("[ ]");
					if(data.length!=2) { 
						throw new CompilerException("Invalid map definition: ["+line+"]");
					}
					currentMap=data[1];
					if(maps.containsKey(currentMap)) {
						throw new CompilerException("Invalid map definition: ["+line+"]");
					}
					maps.put(currentMap, new MapData());
				}else if(line.startsWith(":")) {
					instrLabel=line.substring(1);
					state=STATE_READ_INSTR_START;
				}else if(line.startsWith("#define ")) {
					String name=line.substring("#define".length(),line.indexOf('(')).trim();
					MacroData md=new MacroData(name);
					macros.put(name, md);
					String params=line.substring(line.indexOf('(')+1,line.indexOf(')'));
					for(String s:params.split("[,]")) {
						s=s.trim();
						if(s.length()>0)md.params.add(s);
					}
					
					md.code=line.substring(line.indexOf("#{")+2,line.indexOf("#}")).trim();
				}else
					outStreamMain.println(line);
				break;
				
			case STATE_READ_MAP:
				if(line.startsWith("END")) {
					state=STATE_0;
				}else {
					line=line.trim();
					String[] data=line.split("[ ]");
					if(data.length!=2) {
						throw new CompilerException("Invalid map element definition: ["+line+"]");						
					}
					
					maps.get(currentMap).list.add(new MapElement(data[1],data[0]));
				}
				
				break;
				
			case STATE_READ_INSTR_START:
				// Read the opcode(s); will process at the end
				opcode=line.trim().split("[ ]");
				state=STATE_READ_INSTR_CODE;
				instrCode.setLength(0);
				break;
				
			case STATE_READ_INSTR_CODE:
				// Read the code; will process at the end
				if(line.startsWith(":")) { // Next instruction
					processInstruction(instrLabel,opcode,instrCode.toString().replaceAll("[\r\n]+$", ""), maps);
					if(line.contentEquals(":.")) {
						printOpcodeTable(opcodeTable,1, "", outStreamMain,outStreamMethods);
						outStreamMain.flush();
						outStreamMethods.flush();
						opcodeTable.clear();
						
						state=STATE_0;
					}
					else{
						instrLabel=line.substring(1);
						state=STATE_READ_INSTR_START;
					}
				}else {instrCode.append(line); instrCode.append("\n");}
				break;
				
			default:
				throw new CompilerException("Invalid state: ["+state+"]");
			}
		}
		inReader.close();
		
		if(state==STATE_READ_INSTR_CODE) {
			processInstruction(instrLabel,opcode,instrCode.toString(), maps);
			printOpcodeTable(opcodeTable,1, "", outStreamMain,outStreamMethods);
			outStreamMain.flush();
			outStreamMethods.flush();
		}
		
		//System.out.println(baos.toString());

		MacroExpander mex=new MacroExpander(macros);
		String newcode=mex.expand(baosMain.toString()+baosMethods.toString()+"}");
		//System.out.println(newcode);
		baosMain.reset();
		outStreamMain.println(newcode);
		outStreamMain.flush();
		
		Files.write(Paths.get(args[1]),baosMain.toByteArray());
		
	}

}
