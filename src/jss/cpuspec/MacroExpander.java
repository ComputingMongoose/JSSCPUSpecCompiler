package jss.cpuspec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacroExpander {

	HashMap<String,MacroData> macros;
	
	public MacroExpander(HashMap<String,MacroData> macros) {
		this.macros=macros;
	}
	
	public int indexOfParam(String param, String inputStr) {
		final String separator="[ ,;=(){}.+\\-%^&|*:\\[\\]<>]";
		
	    Pattern pattern = Pattern.compile("^"+param+"$");
	    Matcher matcher = pattern.matcher(inputStr);
	    if(matcher.find())return matcher.start();
	    
	    pattern = Pattern.compile("^"+param+separator);
	    matcher = pattern.matcher(inputStr);
	    if(matcher.find())return matcher.start();

	    pattern = Pattern.compile(separator+param+separator);
	    matcher = pattern.matcher(inputStr);
	    if(matcher.find())return matcher.start()+1;
	    
	    pattern = Pattern.compile(separator+param+"$");
	    matcher = pattern.matcher(inputStr);
	    if(matcher.find())return matcher.start()+1;

	    return -1;
	}
	
	public String expand(String inputString) throws MacroExpansionException {
		StringBuffer ret=new StringBuffer();
		String current=inputString;
		for(boolean wasExpansion=true;wasExpansion;current=ret.toString()) {
			ret.setLength(0);
			wasExpansion=false;
			
			while(current!=null) {
				int pos=current.indexOf("#");
				if(pos==-1) {ret.append(current); current=null; break;}
				
				ret.append(current.substring(0,pos));
				current=current.substring(pos);
				int p2=current.indexOf('(');
				if(p2==-1) {ret.append(current); current=null; break;}
				if(p2>100) {ret.append("#"); current=current.substring(1); continue;}
				
				String name=current.substring(1,p2).trim();
				if(!macros.containsKey(name)) {ret.append("#"); current=current.substring(1); continue;}
				
				wasExpansion=true;
				MacroData md=macros.get(name);
				ArrayList<String> params=new ArrayList<>(20);
				
				current=current.substring(p2+1);
				int depth=0;
				int param_start=0;
				for(pos=0;pos<current.length();pos++) {
					char c=current.charAt(pos);
					if(c=='(')depth++;
					else if(c==')') {
						depth--;
						if(depth==-1) {
							String p=current.substring(param_start,pos).trim();
							if(p.length()>0)params.add(p);
							break;
						}
					}else if(c==',') {
						if(depth==0) {
							params.add(current.substring(param_start,pos));
							param_start=pos+1;
						}
					}
				}
				
				if(depth==-1) { // found end of macro
					current=current.substring(pos+1);
					if(params.size()!=md.params.size())
						throw new MacroExpansionException("Invalid number of parameters ["+name+"]");
					StringBuffer newcode=new StringBuffer();
					newcode.append(md.code);
					for(int i=0;i<params.size();i++) {
						String code=newcode.toString();
						newcode.setLength(0);
						for(boolean found=true;found;) {
							pos=this.indexOfParam(md.params.get(i), code);
							if(pos==-1) {newcode.append(code); found=false; break;}
							newcode.append(code.substring(0,pos));
							newcode.append(params.get(i));
							code=code.substring(pos+md.params.get(i).length());
						}
					}
					ret.append(newcode);
				}else {
					throw new MacroExpansionException("Macro not properly ended ["+name+"]");
				}
			}
		}
		
		
		return ret.toString();
	}
	
}
