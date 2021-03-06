/*
 *Author: Arne Roeters
 */
package fileparser;

import proteindigesters.ChemotrypsinDigesterHighSpecific;
import proteindigesters.PepsinDigesterLowPH;
import proteindigesters.TrypsinDigester;
import proteindigesters.NoDigester;
import proteindigesters.TrypsinDigesterConservative;
import proteindigesters.ChemotrypsinDigesterLowSpecific;
import proteindigesters.Digester;
import proteindigesters.PepsinDigesterHigherPH;
import callable.CallablePeptideMatcher;
import filewriter.CsvFileWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Future;
import collectionobject.Gene;
import collectionobject.GeneCollection;
import collectionobject.PeptideCollection;
import collectionobject.Protein;
import collectionobject.ProteinCollection;
import java.util.Arrays;
import java.util.HashMap;

/**
 *
 * @author arne
 */
public class FastaDatabaseParser {

    /**
     * The path and filename that should be parsed.
     */
    private final String databaseFile;
    /**
     * Contains the type of digestion wanted for the digestion.
     */
    private final Digester digesterType;
    /**
     * The path and filename for the result file.
     */
    private final String resultFileName;
    /**
     * The peptide collection that will hold all peptides.
     */
    private final PeptideCollection peptideCollection = new PeptideCollection();
    /**
     * The number of cores to use.
     */
    private final String coreNr;
    /**
     * the maximum number of proteins a peptide can match to and still be taken
     * into account.
     */
    private final Integer maxProteins;
    /**
     * External peptide collection.
     */
    private PeptideCollection externalPepCol = null;
    /**
     * The Choice for output results is stored here.
     */
    private final String fileOption;

    /**
     * Constructor of the class.
     *
     * @param coreNumber number of cores to use
     * @param maxProteinNr maximum number of protein that can match to a protein
     * @param resultFile path and filename for the result file
     * @param database String of the database fastaFile
     * @param digestionType type of digestion wanted
     * @param fileOption choice of output
     * @param minPepLen minimal length of the peptides to be used
     * @param mc the number of miscleavages to use
     * @throws IOException when directory or files are not found
     */
    public FastaDatabaseParser(final String coreNumber, final Integer maxProteinNr, String resultFile,
            final String database, final String digestionType, final Integer minPepLen, final String fileOption,
            final Integer mc)
            throws IOException, Exception {
        this.databaseFile = database;
        this.digesterType = getDigester(digestionType, minPepLen, mc);
        if (!resultFile.endsWith("/")) {
            resultFile = resultFile + "/";
        }
        String filename = database.split("/")[database.split("/").length - 1];
        this.resultFileName = resultFile + filename.replace(".fasta", "");
        System.out.println("The final results will be placed in: " + resultFile);
        this.coreNr = coreNumber;
        this.maxProteins = maxProteinNr;
        this.fileOption = fileOption;
    }

    /**
     * Constructor of the class.
     *
     * @param coreNumber number of cores to use
     * @param maxProteinNr maximum number of protein that can match to a protein
     * @param resultFile path and filename for the result file
     * @param database String of the database fastaFile
     * @param digestionType type of digestion wanted
     * @param minPepLen minimal length of the peptides to be used
     * @param fileOption choice of output
     * @param external a external peptide collection
     * @param mc the number of miscleavages to use
     * @throws IOException when directory or files are not found
     */
    public FastaDatabaseParser(final String coreNumber, final Integer maxProteinNr, String resultFile,
            final String database, final String digestionType, final Integer minPepLen, final String fileOption, 
            final PeptideCollection external, final Integer mc)
            throws IOException, Exception {
        this.databaseFile = database;
        this.digesterType = getDigester(digestionType, minPepLen, mc);
        if (!resultFile.endsWith("/")) {
            resultFile = resultFile + "/";
        }
        String filename = database.split("/")[database.split("/").length - 1];
        if (filename.contains(".fasta")) {
            this.resultFileName = resultFile + filename.replace(".fasta", "");
        } else {
            this.resultFileName = resultFile + filename.replace(".fa", "");
        }
        this.coreNr = coreNumber;
        this.maxProteins = maxProteinNr;
        this.fileOption = fileOption;
        this.externalPepCol = external;
        System.out.println("The final results will be placed in: " + resultFile);
    }

    /**
     * Reads the file and parses it.
     *
     * @return ProteinCollection with all proteins in it
     * @throws FileNotFoundException if the file not exists
     * @throws IOException .
     */
    public final ProteinCollection getProteinCollection()
            throws IOException, FileNotFoundException, Exception {
        System.out.println("Collecting proteins from: " + this.databaseFile);
        ProteinCollection pc = new ProteinCollection();
        File file2Parse = new File(this.databaseFile);
        BufferedReader br = new BufferedReader(new FileReader(file2Parse.getPath()));
        String line;
        String name = "";
        Protein protein = new Protein();
        Boolean notValidProtein = false;
        ArrayList<String> peptides;
        while ((line = br.readLine()) != null) {
            if (!notValidProtein) {
                if (line.startsWith(">GENSCAN")) {
                    notValidProtein = true;
                } else if (line.startsWith(">")) {
                    if (protein.getName() != null) {
                        peptides = this.digesterType.digest(protein.getSequence());
                        for (String peptide : peptides) {
                            protein.addTotalPeptides(peptideCollection.addPeptide(peptide));
                        }
                        pc.addProtein(name, protein);
                        protein = new Protein();
                    }
                    if (line.startsWith(">sp") || line.startsWith(">tr")) {
                        name = line.split("\\|")[1];
                    } else if (line.startsWith(">EN") || line.startsWith(">NEWP")) {
                        if (line.contains("_")) {
                            name = line.split("_")[0].replace(">", "");
                        } else {
                            name = line.trim().replace(">", "");
                        }
                    } else {
                        name = line.substring(1).replaceAll("\\s", "");
                    }
                    protein.setName(name);
                } else {
                    protein.setSequence(line);
                }
            } else if (notValidProtein) {
                notValidProtein = false;
            }
        }
        System.out.println(pc.getProteinNames().size() + " proteins collected");
        return pc;
    }

    /**
     * writes the uniqueness of the peptide per protein to a file.
     *
     * @param proteinCollection a filled protein collection
     * @param ensemblFile the ensembl file
     * @throws java.io.IOException when input or output gives an error
     * @throws Exception when an error occurs
     */
    public final void getPeptideUniqueness(final ProteinCollection proteinCollection, final String ensemblFile) throws IOException, Exception {
        // Get all matches per peptide with the given database
        EnsemblFileParser edc = new EnsemblFileParser();
        HashMap<String, String> ensemblMap = edc.getEnsemblID(ensemblFile);
        Set<Future<LinkedList<String>>> set;
        PeptideCollection UsedPeptideCollection;
        if (externalPepCol == null) {
            UsedPeptideCollection = peptideCollection;
        } else {
            UsedPeptideCollection = externalPepCol;
        }
        set = getPeptideMatches(UsedPeptideCollection, proteinCollection);
        long start = System.currentTimeMillis();
        // Create a geneCollection which will hold the genes that are needed for the output
        GeneCollection geneCol = new GeneCollection();
        // Create all variables that are needed
        HashMap<String, ArrayList<String>> geneMap;
        LinkedList<String> futureSet;
        Gene gene;
        Integer peptideIndex;
        HashMap<String, Integer> excessivePeptides = new HashMap<>();
        String proteinNameConverted;
        ArrayList<String> proteinList;
        // Parse all results
        for (Future<LinkedList<String>> future : set) {
            futureSet = future.get();
            if (futureSet.size() > this.maxProteins) {
                excessivePeptides.put(futureSet.get(0), futureSet.size());
            } else {
                peptideIndex = UsedPeptideCollection.getPeptideIndex(futureSet.get(0));
                geneMap = new HashMap<>();
                for (String proteinName : futureSet.subList(1, futureSet.size())) {
                    proteinNameConverted = ensemblMap.get(proteinName);
                    if (geneMap.containsKey(proteinNameConverted)) {
                        geneMap.get(proteinNameConverted).add(proteinName);
                    } else {
                        proteinList = new ArrayList<>(Arrays.asList(proteinName));
                        geneMap.put(proteinNameConverted, proteinList);
                    }
                }
                for (String geneName : geneMap.keySet()) {
                    if (geneMap.size() == 1) {
                        if (geneCol.checkGene(geneName)) {
                            gene = geneCol.getGene(geneName);
                            gene.addUniquePeptide(peptideIndex);
                            for (String proteinName : geneMap.get(geneName)) {
                                gene.addTotalPeptides(proteinCollection.getProtein(proteinName).getTotalPeptides());
                            }
                        } else {
                            gene = new Gene(geneName);
                            gene.addUniquePeptide(peptideIndex);
                            for (String proteinName : geneMap.get(geneName)) {
                                gene.addTotalPeptides(proteinCollection.getProtein(proteinName).getTotalPeptides());
                            }
                            geneCol.addGene(geneName, gene);
                        }
                    } else {
                        if (geneCol.checkGene(geneName)) {
                            gene = geneCol.getGene(geneName);
                            gene.addNonUniquePeptide(peptideIndex);
                            for (String proteinName : geneMap.get(geneName)) {
                                gene.addTotalPeptides(proteinCollection.getProtein(proteinName).getTotalPeptides());
                            }
                        } else {
                            gene = new Gene(geneName);
                            gene.addNonUniquePeptide(peptideIndex);
                            for (String proteinName : geneMap.get(geneName)) {
                                gene.addTotalPeptides(proteinCollection.getProtein(proteinName).getTotalPeptides());
                            }
                            geneCol.addGene(geneName, gene);
                        }
                    }

                }
                if (futureSet.size() == 2) {
                    proteinCollection.getProtein(futureSet.get(1)).addUniquePeptide(peptideIndex);
                } else {
                    for (int i = 1; i < futureSet.size(); i++) {
                        proteinCollection.getProtein(futureSet.get(i)).addNonUniquePeptide(peptideIndex);
                    }
                }
            }
        }
        long end = System.currentTimeMillis() - start;
        System.out.println("Matching the peptides took " + end / 1000 + " seconds");
        CsvFileWriter cfw;
        if (externalPepCol == null) {
            cfw = new CsvFileWriter(this.resultFileName, UsedPeptideCollection, Integer.parseInt(this.coreNr));
        } else {
            cfw = new CsvFileWriter(this.resultFileName, UsedPeptideCollection, Integer.parseInt(this.coreNr), peptideCollection);
        }
        // Write all results to the files
        cfw.writeExcessPeptides(excessivePeptides);
        if (!geneCol.getGeneNames().isEmpty()) {
            switch (this.fileOption) {
                case "b":
                    cfw.writeGeneUniquenessToCsv(geneCol);
                    cfw.writeProteinUniquenessToCsv(proteinCollection);
                    break;
                case "g":
                    cfw.writeGeneUniquenessToCsv(geneCol);
                    break;
                case "p":
                    cfw.writeProteinUniquenessToCsv(proteinCollection);
                    break;
            }
        }
    }

    /**
     * Get the enzyme for digestion to use.
     *
     * @param digestionType method for digesting the peptides
     * @param minPepLen minimal peptide length
     * @return the Digester
     */
    private Digester getDigester(final String digestionType, final Integer minPepLen, final Integer mc) {
        switch (digestionType) {
            case "0": {
                return new NoDigester(minPepLen, mc);
            }
            case "1": {
                return new TrypsinDigesterConservative(minPepLen, mc);
            }
            case "2": {
                return new TrypsinDigester(minPepLen, mc);
            }
            case "3": {
                return new PepsinDigesterHigherPH(minPepLen, mc);
            }
            case "4": {
                return new PepsinDigesterLowPH(minPepLen, mc);
            }
            case "5": {
                return new ChemotrypsinDigesterHighSpecific(minPepLen, mc);
            }
            case "6": {
                return new ChemotrypsinDigesterLowSpecific(minPepLen, mc);
            }
            default: {
                return new NoDigester(minPepLen, mc);
            }
        }
    }

    /**
     * Returns a future set filled with all peptide matches
     *
     * @param pepCol a peptide collection
     * @param proteinCollection the protein collection
     * @return a future set of the peptide matches
     * @throws Exception when an error occurs
     */
    public final Set<Future<LinkedList<String>>> getPeptideMatches(final PeptideCollection pepCol,
            final ProteinCollection proteinCollection) throws Exception {
        CallablePeptideMatcher searcher = new CallablePeptideMatcher(pepCol, proteinCollection);
        return searcher.matchPeptides(Integer.parseInt(this.coreNr));
    }
}
