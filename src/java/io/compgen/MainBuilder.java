package io.compgen;

import io.compgen.annotation.Command;
import io.compgen.annotation.Option;
import io.compgen.annotation.UnnamedArg;
import io.compgen.exceptions.CommandArgumentException;
import io.compgen.exceptions.MissingCommandException;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class MainBuilder {
	public class CmdArgs {
		public final Map<String, String> cmdargs;
		public final List<String> unnamed;

		public CmdArgs(Map<String, String> cmdargs, List<String> unnamed) {
			this.cmdargs = Collections.unmodifiableMap(cmdargs);
			this.unnamed = (unnamed == null) ? null: Collections.unmodifiableList(unnamed);
		}
	}

	private static Map<String, Class<? extends Exec>> execs = new HashMap<String, Class<? extends Exec>>();

	private String defaultCategory = "General";
	private String progname = null;
	private String usage = null;
	private String version = null;
	
	public MainBuilder setProgName(String progname) {
		this.progname = progname;
		return this;
	}

	public MainBuilder setUsage(String usage) {
		this.usage = usage;
		return this;
	}

	public MainBuilder setVersion(String version) {
		this.version = version;
		return this;
	}

	public MainBuilder setDefaultCategory(String defaultCategory) {
		this.defaultCategory = defaultCategory;
		return this;
	}

	
	public MainBuilder addCommand(Class<? extends Exec> clazz) {
		String name = clazz.getSimpleName();
		Command annotation = clazz.getAnnotation(Command.class);
		if (annotation != null) {
			name = annotation.name();
		}
		execs.put(name, clazz);
		return this;
	}

	public void showCommands() throws MissingCommandException {
		showCommands(System.err);
	}

	public void showCommands(OutputStream out) throws MissingCommandException {
		PrintStream ps = new PrintStream(out);
		if (usage != null) {
			ps.println(usage);
			ps.println();
		}
		ps.println("Available commands:");

		boolean hasExperimental = false;
		
		int minsize = 4;
		String spacer = "    ";
		for (String cmd : execs.keySet()) {
			if (cmd.length() > minsize) {
	            Command c = execs.get(cmd).getAnnotation(Command.class);
	            if (c.experimental()) {
	            	hasExperimental = true;
	            }
	            if (c.deprecated()) {
	                continue;
	            }
                minsize = cmd.length();
	            if (c.experimental()) {
	                minsize += 1;
	            }
			}
		}
		Map<String, List<String>> progs = new HashMap<String, List<String>>();

		for (String cmd : execs.keySet()) {
			Command c = execs.get(cmd).getAnnotation(Command.class);
			if (c != null) {
                if (c.deprecated() || c.hidden()) {
                    continue;
                }
                
                String cat = c.category().equals("") ? defaultCategory: c.category();
                
				if (!progs.containsKey(cat)) {
					progs.put(cat, new ArrayList<String>());
				}

				if (!c.desc().equals("")) {
					spacer = "";
					for (int i = c.experimental() ? cmd.length()+1: cmd.length(); i < minsize; i++) {
						spacer += " ";
					}
					spacer += " - ";
					if (c.experimental()) { 
                        progs.get(cat).add("  " + cmd + "*" + spacer + c.desc());
                    } else {
					    progs.get(cat).add("  " + cmd + spacer + c.desc());
				    }   
				} else {
                    if (c.experimental()) { 
                        progs.get(cat).add("  " + cmd + "*");
                    } else {
                        progs.get(cat).add("  " + cmd);
                    }   
				}
			} else {
				throw new MissingCommandException("Command: "+cmd+ "missing @Command annotation");
			}
		}

		List<String> cats = new ArrayList<String>(progs.keySet());
		Collections.sort(cats);

		for (String cat : cats) {
            ps.println("");
            ps.println("[" + cat + "]");
			Collections.sort(progs.get(cat));
			for (String line : progs.get(cat)) {
				ps.println(line);
			}
		}

//		spacer = "";
//		for (int i = 12; i < minsize; i++) {
//			spacer += " ";
//		}
//		spacer += " - ";
//		System.err.println("[help]");
//        System.err.println("  help command" + spacer
//                + "Help message for the given command");
//        System.err.println("  license     " + spacer
//                + "Display licenses");
		
        if (hasExperimental) {
        	ps.println();
        	ps.println("* = experimental command");
        }
        if (version != null) {
        	ps.println();
        	ps.println(version);
        }
    	ps.println();
	}

	public void showCommandHelp(String cmd) throws MissingCommandException {
		showCommandHelp(cmd, System.err);
	}

	public void showCommandHelp(String cmd, OutputStream out) throws MissingCommandException {
		if (!execs.containsKey(cmd)) {
			throw new MissingCommandException();
		}

		PrintStream ps = new PrintStream(out);
		
		Command command = execs.get(cmd).getAnnotation(Command.class);
		ps.print(command.name());
		if (!command.desc().equals("")) {
			ps.print(" - " + command.desc());
		}
		if (!command.doc().equals("")) {
			ps.println();
			ps.print(command.doc());
		}
		ps.println();
		ps.print("Usage:" + (progname == null ? "" : " " +progname )+ " "+ command.name() + " [options]");
		for (Method m: execs.get(cmd).getMethods()) {
			UnnamedArg uarg = m.getAnnotation(UnnamedArg.class);
			if (uarg != null) {
				ps.print(" "+uarg.name());
			}
		}
		ps.println();
		ps.println();
		ps.println("Options:");

		SortedMap<String, String> opts = new TreeMap<String, String>();
		int minsize = 4;
		for (Method m: execs.get(cmd).getMethods()) {
			Option opt = m.getAnnotation(Option.class);
			if (opt != null) {
				String k = "";
				if (!opt.name().equals("")) {
					k = opt.name();
					if (!opt.charName().equals("")) {
						k += " -"+opt.charName();
					}
					k += "  ";
				} else if (!opt.charName().equals("")) {
					k = opt.charName() + " ";
				} else {
					if (m.getName().startsWith("set")) {
						k = m.getName().substring(3).toLowerCase() + "  ";
					} else {
						k = m.getName().toLowerCase() + "  ";
					}
				}
				String desc = opt.desc();
				if (!opt.defaultValue().equals("")) {
					desc += " (default: " + opt.defaultValue()+ ")";
				}
				opts.put(k, desc);
				minsize = minsize > k.length() ? minsize: k.length();
			}
		}
		
		for (String k:opts.keySet()) {
			String spacer = "";
			for (int i=k.length(); i<minsize; i++) {
				spacer += " ";
			}
			if (k.endsWith("  ")) {
				ps.println("  --"+k.substring(0,k.length()-2)+spacer+" : "+opts.get(k));
			} else {
				ps.println("  -"+k.substring(0,k.length()-1)+spacer+" : "+opts.get(k));
			}
		}
		
		if (version != null) {
			ps.println();
			ps.println(version);
		}
    	ps.println();
	}

	public void findAndRun(String[] args) throws Exception {
		Exec exec = null;
		List<String> errors = new ArrayList<String>();
		
		if (!execs.containsKey(args[0])) {
			throw new MissingCommandException();
		}
		
		
		Class<? extends Exec> clazz = execs.get(args[0]);
		exec = clazz.newInstance();
		CmdArgs cmdargs = extractArgs(args, clazz); 

		for (Method m: clazz.getMethods()) {
			Option opt = m.getAnnotation(Option.class);
			if (opt != null) {
				if (opt.showHelp()) {
					showCommandHelp(args[0]);
					System.exit(1);
				}
				String val = null;
				if (cmdargs.cmdargs.containsKey(opt.charName())) {
					val = cmdargs.cmdargs.get(opt.charName());
				} else if (cmdargs.cmdargs.containsKey(opt.name())) {
					val = cmdargs.cmdargs.get(opt.name());
				} else {
					if (m.getName().startsWith("set")) {
						String k = m.getName().substring(3).toLowerCase();
						val = cmdargs.cmdargs.get(k);
					} else {
						String k = m.getName().toLowerCase();
						val = cmdargs.cmdargs.get(k);
					}
				}
				
				if (val == null) {
					// missing value, try defaults
					if (opt.required()) {
						errors.add("Missing argument: "+opt.name());
					} else if (!opt.defaultValue().equals("")) {
						invokeMethod(exec, m, opt.defaultValue());
					}
				} else if (val.equals("")) {
					// naked option w/o value
					invokeMethodBoolean(exec, m, true);
				} else {
					invokeMethod(exec, m, val);
				}
				continue;
			}
			
			UnnamedArg unnamed = m.getAnnotation(UnnamedArg.class);
			if (unnamed != null) {
				if (cmdargs.unnamed == null) {
					if (unnamed.required()) {
						errors.add("Missing argument: "+unnamed.name());
					} else if (!unnamed.defaultValue().equals("")) {
						invokeMethod(exec, m, unnamed.defaultValue());
					}
					continue;
				}

				if (m.getParameterTypes()[0].isArray()) {					
					String[] ar = (String[]) cmdargs.unnamed.toArray(new String[cmdargs.unnamed.size()]);
					m.invoke(exec, (Object) ar);
				} else if (m.getParameterTypes()[0].equals(List.class)) {
					m.invoke(exec, Collections.unmodifiableList(cmdargs.unnamed));				
				} else {
					invokeMethod(exec, m, cmdargs.unnamed.get(0));
				}
			}
		}
		
		if (errors.size() == 0) {
			try {
				exec.exec();
			} catch (CommandArgumentException e) {
				System.err.println("ERROR: " + e.getMessage());
				System.err.println();
				showCommandHelp(args[0]);
				System.exit(1);
			}
		} else {
			for (String error: errors) {
				System.err.println(error);
				System.err.println();
				showCommandHelp(args[0]);
				System.exit(1);
			}
		}
	}

	private boolean isOptionBoolean(Class<?> clazz, String name) {
		for (Method m:clazz.getMethods()) {
			Option opt = m.getAnnotation(Option.class);
			if (opt != null && (opt.name().equals(name) || opt.charName().equals(name))) {
				Class<?> param = m.getParameterTypes()[0];
				if (param.equals(Boolean.class) || param.equals(Boolean.TYPE)) {
					return true;
				}
			}
		}
		return false;
	}
	
	private CmdArgs extractArgs(String[] args, Class<?> clazz) {
		Map<String, String> cmdargs = new HashMap<String, String>();
		List<String> unnamed = null;
		
		int i=1;
		while (i < args.length) {
			String arg = args[i];
			if (unnamed != null) {
				unnamed.add(arg);
				i++;
				continue;
			}
			if (arg.startsWith("--")) {
				if (i+1 < args.length) {
					if (args[i+1].startsWith("-") || isOptionBoolean(clazz, arg.substring(2))) {
						cmdargs.put(arg.substring(2), "");
						i += 1;
						continue;
					} else {
						cmdargs.put(arg.substring(2), args[i+1]);
						i += 2;
						continue;						
					}
				} else {
					cmdargs.put(arg, "");
					i += 1;
				}
			} else if (arg.startsWith("-")) {
				for (int j=1; j<arg.length(); j++) {
					if (j == arg.length()-1) {
						if (args.length == i+1 || args[i+1].startsWith("-") || isOptionBoolean(clazz, ""+arg.charAt(j))) {
							cmdargs.put(""+arg.charAt(j), "");
							i += 1;
							continue;
						} else {
							cmdargs.put(""+arg.charAt(j), args[i+1]);
							i += 2;
							continue;						
						}
					} else {
						cmdargs.put(""+arg.charAt(j), "");
					}
				}
			} else {
				unnamed = new ArrayList<String>();
				unnamed.add(arg);
				i++;
			}
		}
		
		return new CmdArgs(cmdargs, unnamed);
	}

	public void invokeMethod(Object obj, Method m, String val) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, CommandArgumentException {
		Class<?> param = m.getParameterTypes()[0];
		if (val == null) {
			m.invoke(obj, new Object[] {null});
		} else if (param.equals(String.class)) {
			m.invoke(obj, val);
		} else if (param.equals(Integer.class) || param.equals(Integer.TYPE)) {
			m.invoke(obj, Integer.parseInt(val));
		} else if (param.equals(Long.class) || param.equals(Long.TYPE)) {
			m.invoke(obj, Long.parseLong(val));
		} else if (param.equals(Float.class) || param.equals(Float.TYPE)) {
			m.invoke(obj, Float.parseFloat(val));
		} else if (param.equals(Double.class) || param.equals(Double.TYPE)) {
			m.invoke(obj, Double.parseDouble(val));
		} else {
			throw new CommandArgumentException(m, param.getName(), param.getClass(), val );
		}
	}

	public void invokeMethodBoolean(Object obj, Method m, boolean val) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, CommandArgumentException {
		if (m.getParameterTypes().length == 0) {
			m.invoke(obj);
		} else {
			Class<?> param = m.getParameterTypes()[0];
			if (param.equals(Boolean.class) || param.equals(Boolean.TYPE)) {
				m.invoke(obj, val);
			} else {
				throw new CommandArgumentException(m, "");
			}
		}
	}

}