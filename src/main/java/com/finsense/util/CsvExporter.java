package com.finsense.util;

import com.finsense.model.Transaction;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Plain java.io/java.nio CSV export with RFC-style quoting for commas and quotes. */
public final class CsvExporter {
    private CsvExporter() { }
    public static void exportTransactions(Path file,List<Transaction> rows) throws IOException {
        try(BufferedWriter writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8)) {
            writer.write("Date,Type,Category,Amount,Description"); writer.newLine();
            for(Transaction row:rows) {
                writer.write(csv(row.date().toString())); writer.write(',');
                writer.write(csv(row.type().name())); writer.write(',');
                writer.write(csv(row.category().name())); writer.write(',');
                writer.write(csv(row.amount().toPlainString())); writer.write(',');
                writer.write(csv(row.description()==null?"":row.description())); writer.newLine();
            }
        }
    }
    /** Quotes fields with commas/quotes/newlines and doubles embedded quote marks. */
    public static String csv(String value) {
        if(value==null) return "";
        if(value.indexOf(',')<0 && value.indexOf('"')<0 && value.indexOf('\n')<0 && value.indexOf('\r')<0) return value;
        return '"'+value.replace("\"","\"\"")+'"';
    }
}
