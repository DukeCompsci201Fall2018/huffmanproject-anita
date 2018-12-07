import java.util.PriorityQueue;

/**
 * Although this class has a history of several years, it is starting from a
 * blank-slate, new and clean implementation as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information and including
 * debug and bits read/written information
 *
 * @author Owen Astrachan
 *
 *         Students: JJ Jiang (jj252) & Anita Li (al367)
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;


	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in  Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
		/*
		 * while (true) { int val = in.readBits(BITS_PER_WORD); if (val == -1) break;
		 * out.writeBits(BITS_PER_WORD, val); } out.close();
		 */
	}

	/**
	 * This method determines how frequently a character/chunk occurs in a stream
	 *
	 * @param in is the stream
	 * @return an array with the amount of times each chunk occurs
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1]; // initialize an array for the frequency
		while (true) { // continue looping through until broken
			int curr = in.readBits(BITS_PER_WORD); // read from stream
			if (curr == -1) // if there are no bits left
				break; // break out of the loop
			else // if there are bits
				freq[curr] = freq[curr] + 1; // increase frequency of that bit
		}
		freq[PSEUDO_EOF] = 1; // set frequency of error to be 1
		return freq; //  return the frequency array
	}

	/**
	 * This method creates a Huffman Tree using the array of how "heavy" (frequently) a chunk is
	 * @param counts is the array with the frequencies
	 * @return the first node
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		//use a greedy algorithm and a priority queue to create the trie
		//remove the minimal-weight nodes
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();

		for (int i = 0; i < counts.length; i++) {
			if (counts[i] >= 0)
				pq.add(new HuffNode(i,counts[i],null,null));
		}

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			//value = 1 for being a leaf in the tree
			HuffNode t = new HuffNode(1, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		//returns an array of Strings such that a[val] is the encoding for val
		String[] encodings = new String[ALPH_SIZE + 1];
		findPaths(root, "", encodings);

		return encodings;
	}

	private void findPaths (HuffNode root, String path, String[] encodings){
		//if root is a leaf, an encoding for the value stored in the leaf is added to the array
		if (root.myLeft == null && root.myRight == null){
			encodings[root.myValue] = path;

			//print encodings for each root-to-leaf path found
			//FOR DEBUGGING
//			if (myDebugLevel >= DEBUG_HIGH) {
//				System.out.printf("encoding for %d is %s\n", root.myValue, path);
//			}
//			System.out.printf("encoding for %d is %s\n", root.myValue, path);

			return;
		}

		//recursive calls adding "0" to call to left subtree; adding "1" to call to right subtree
		findPaths(root.myLeft, path + "0", encodings);
		findPaths(root.myRight, path + "1", encodings);
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		//if the node is a leaf, write a single bit of one followed by nine bits of the value stored in leaf
		if (root.myLeft == null && root.myRight == null){
			out.writeBits(1, 1);
			out.writeBits(9, root.myValue);
			return;
		}

		//if node is an internal node (not a leaf) write a single bit of zero
		out.writeBits(1, 0);

		//if a node is an internal node, recursive call to each subtree (myLeft and myRight)
		writeHeader(root.myLeft, out);
		writeHeader(root.myRight, out);
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		//read the file compressed one more time to compress
		//encoding for each 8-bit chunck read is stored
		//convert string of "0" and "1" into bit-sequence using Integer.parseInt

		//reset the BitInputStream
		in.reset();

		//read BitInputStream again to compress it
		int bitCode = in.readBits(BITS_PER_WORD);

		//convert encoding strings into a bit-sequence
		while (bitCode >= 0) {
			String code = codings[bitCode];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			bitCode = in.readBits(BITS_PER_WORD);
		}

		//write the bits that encode PSEUDO_EOF
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));

	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in  Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		int bits = in.readBits(BITS_PER_INT); // read the amount of bits
		if (bits != HUFF_TREE) { // if it doesn't start correctly
			throw new HuffException("illegal header starts with " + bits); // throw an exception
		}
		HuffNode root = readTreeHeader(in); // create a node to store the root node and call on method to create tree
		readCompressedBits(root, in, out); // call on method to read tree and write to output

		out.close(); // close output file
	}

	/**
	 * This method creates a tree of HuffNodes, in which 0 values are stored as tree
	 * nodes and 1s, followed by letters are stored as leaf nodes
	 *
	 * @param in is the bitstream that must be converted to a tree
	 * @return the HuffNode at the top, the root
	 */
	private HuffNode readTreeHeader(BitInputStream in) {

		int bits = in.readBits(1); // read the first bit
		if (bits == -1) // if the bit equals -1 (isn't valid)
			throw new HuffException(bits + "is not a valid bit"); // throw an exception
		if (bits == 0) { // if bit is equal to 0
			HuffNode left = readTreeHeader(in); // call the method again to construct the left node
			HuffNode right = readTreeHeader(in); // call the method again to construct the right node
			return new HuffNode(0, 0, left, right); // return a new HuffNode with the new left and right nodes
		} else { // if the bit is an actual value (1)
			int val = in.readBits(BITS_PER_WORD + 1); // read the next bit (a number that corresponds to a letter)
			return new HuffNode(val, 0, null, null); // store that in a leaf node
		}

	}

	/**
	 * This method reads the tree created by the treeHeader
	 *
	 * @param root is the node that is the root of the tree
	 * @param in   is the input stream
	 * @param out  is the end result that needs to be written to
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root; // create a huffnode that starts at the root
		while (true) { // while there are still nodes
			int bits = in.readBits(1); // read the input stream
			if (bits == -1) { // if the input stream isn't valid
				throw new HuffException("bad input, no PSEUDO_EOF"); // throw an error
			} else { // if it is valid
				if (bits == 0) // if the bit is 0
					current = current.myLeft; // set current to get the left node
				else // if the bit isn't 0
					current = current.myRight; // set current to get the right node

				if (current.myLeft == null && current.myRight == null) { // if there are no more nodes under it (aka if
					// it's a leaf)
					if (current.myValue == PSEUDO_EOF) // check if it's an error
						break; // if it is, break out of the loop
					else { // if it's not, then it stores a value
						out.writeBits(BITS_PER_WORD, current.myValue); // write that value to output file
						current = root; // start back at the top after leaf
					}
				}
			}
		}

	}
}
