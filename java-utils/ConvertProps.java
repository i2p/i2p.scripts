package gnu.getopt;

import java.io.*;
import java.util.*;

/**
 *  To fix bad properties files found in gnu.getopts
 *
 *  Adapted from http://www.cs.technion.ac.il/~imaman/programs/unicodeprops.html
 *  zzz 2/2014 public domain
 */
public class ConvertProps {

   /**
    *  Usage: ConvertProps [-t encoding] file
    *
    *  Makes two output files:
    *   1) file.new the properties file with correct \ u escapes,
    *      to replace the old properties file
    *   2) file.utf8 the properties file re-encoded to UTF-8,
    *      for review to see if you got the input encoding right
    *
    *  Side effects / fixes:
    *    - Removes blank lines
    *    - Removes \r
    *    - Removes certain BOMs
    *    - Replaces ^/ with ^# to correct bad comment lines
    *  Bugs:
    *    - Does \ u escapes even in comments
    *    - Escapes even valid ISO-8859-1 above 0x7f
    */
   public static void main(String[] args) {
       try {
           main2(args);
       } catch (IOException ioe) {
           ioe.printStackTrace();
       }
   }

   private static void main2(String[] args) throws IOException {
       if (args.length == 3 && args[0].equals("-t")) {
           loadProperties(args[2], args[1]);
       } else if (args.length == 1) {
           loadProperties(args[0]);
       } else {
           System.err.println("Usage: ConvertProps [-t encoding] file");
       }
   }

   public static Properties loadProperties(String f) throws IOException
   {
      return loadProperties(f, "UTF-8");
   }

   public static Properties loadProperties(String f, String encoding) throws IOException
   {
      InputStream is = new FileInputStream(f);
      StringBuilder sb = new StringBuilder(2048);
      InputStreamReader isr = new InputStreamReader(is, encoding);
      boolean nl = true;
      while(true)
      {
         int temp = isr.read();
         if(temp < 0)
            break;
         // strip DOS \r and BOMs
         if (temp == '\r' || temp == 0xfffd || temp == 0xfeff)
            continue;
         // strip blank lines
         if (nl && temp == '\n')
            continue;
         // fix comments
         if (nl && temp == '/')
             temp = '#';
         nl = temp == '\n';

         char c = (char) temp;
         sb.append(c);
      }

      Writer w1 = new OutputStreamWriter(new FileOutputStream(f + ".utf8"), "UTF-8");
      w1.write(sb.toString());
      w1.close();
      String inputString = escapifyStr(sb.toString());
      Writer w2 = new OutputStreamWriter(new FileOutputStream(f + ".new"), "UTF-8");
      w2.write(inputString);
      w2.close();
      // following code is unused
      byte[] bs = inputString.getBytes("ISO-8859-1");
      ByteArrayInputStream bais = new ByteArrayInputStream(bs);

      Properties ps = new Properties();
      ps.load(bais);
      return ps;
   }
      
   private static char hexDigit(char ch, int offset)
   {
      int val = (ch >> offset) & 0xF;
      if(val <= 9)
         return (char) ('0' + val);
      
      return (char) ('A' + val - 10);
   }

   
   private static String escapifyStr(String str)
   {      
      StringBuilder result = new StringBuilder();

      int len = str.length();
      for(int x = 0; x < len; x++)
      {
         char ch = str.charAt(x);
         if(ch <= 0x007e)
         {
            result.append(ch);
            continue;
         }
         
         result.append('\\');
         result.append('u');
         result.append(hexDigit(ch, 12));
         result.append(hexDigit(ch, 8));
         result.append(hexDigit(ch, 4));
         result.append(hexDigit(ch, 0));
      }
      return result.toString();
   }
}


	
