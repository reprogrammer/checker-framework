package java.util;



public class Collections {
  public final static java.util.Set EMPTY_SET = new HashSet();
  public final static java.util.List EMPTY_LIST = new LinkedList();
  public final static java.util.Map EMPTY_MAP = new HashMap();
  public static <T extends java.lang.Comparable<? super T>> void sort(java.util.List<T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> void sort(java.util.List<T> a1, java.util.Comparator<? super T> a2) { throw new RuntimeException("skeleton method"); }
  public static <T> int binarySearch(java.util.List<? extends java.lang.Comparable<? super T>> a1, T a2) { throw new RuntimeException("skeleton method"); }
  public static <T> int binarySearch(java.util.List<? extends T> a1, T a2, java.util.Comparator<? super T> a3) { throw new RuntimeException("skeleton method"); }
  public static void reverse(java.util.List<?> a1) { throw new RuntimeException("skeleton method"); }
  public static void shuffle(java.util.List<?> a1) { throw new RuntimeException("skeleton method"); }
  public static void shuffle(java.util.List<?> a1, java.util.Random a2) { throw new RuntimeException("skeleton method"); }
  public static void swap(java.util.List<?> a1, int a2, int a3) { throw new RuntimeException("skeleton method"); }
  public static <T> void fill(java.util.List<? super T> a1, T a2) { throw new RuntimeException("skeleton method"); }
  public static <T> void copy(java.util.List<? super T> a1, java.util.List<? extends T> a2) { throw new RuntimeException("skeleton method"); }
  public static <T extends java.lang.Comparable<? super T>> T min(java.util.Collection<? extends T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> T min(java.util.Collection<? extends T> a1, java.util.Comparator<? super T> a2) { throw new RuntimeException("skeleton method"); }
  public static <T extends java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> T max(java.util.Collection<? extends T> a1, java.util.Comparator<? super T> a2) { throw new RuntimeException("skeleton method"); }
  public static void rotate(java.util.List<?> a1, int a2) { throw new RuntimeException("skeleton method"); }
  public static <T> boolean replaceAll(java.util.List<T> a1, T a2, T a3) { throw new RuntimeException("skeleton method"); }
  public static int indexOfSubList(java.util.List<?> a1, java.util.List<?> a2) { throw new RuntimeException("skeleton method"); }
  public static int lastIndexOfSubList(java.util.List<?> a1, java.util.List<?> a2) { throw new RuntimeException("skeleton method"); }
  public static <T> @NonNull Collection<T> unmodifiableCollection(java.util.Collection<? extends T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> @NonNull Set<T> unmodifiableSet(java.util.Set<? extends T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> @NonNull SortedSet<T> unmodifiableSortedSet(java.util.SortedSet<T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> @NonNull List<T> unmodifiableList(java.util.List<? extends T> a1) { throw new RuntimeException("skeleton method"); }
  public static <K, V> @NonNull Map<K, V> unmodifiableMap(java.util.Map<? extends K, ? extends V> a1) { throw new RuntimeException("skeleton method"); }
  public static <K, V> java.util.SortedMap<K, V> unmodifiableSortedMap(java.util.SortedMap<K, ? extends V> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.Collection<T> synchronizedCollection(java.util.Collection<T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.Set<T> synchronizedSet(java.util.Set<T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.SortedSet<T> synchronizedSortedSet(java.util.SortedSet<T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.List<T> synchronizedList(java.util.List<T> a1) { throw new RuntimeException("skeleton method"); }
  public static <K, V> java.util.Map<K, V> synchronizedMap(java.util.Map<K, V> a1) { throw new RuntimeException("skeleton method"); }
  public static <K, V> java.util.SortedMap<K, V> synchronizedSortedMap(java.util.SortedMap<K, V> a1) { throw new RuntimeException("skeleton method"); }
  public static <E> java.util.Collection<E> checkedCollection(java.util.Collection<E> a1, java.lang.Class<E> a2) { throw new RuntimeException("skeleton method"); }
  public static <E> java.util.Set<E> checkedSet(java.util.Set<E> a1, java.lang.Class<E> a2) { throw new RuntimeException("skeleton method"); }
  public static <E> java.util.SortedSet<E> checkedSortedSet(java.util.SortedSet<E> a1, java.lang.Class<E> a2) { throw new RuntimeException("skeleton method"); }
  public static <E> java.util.List<E> checkedList(java.util.List<E> a1, java.lang.Class<E> a2) { throw new RuntimeException("skeleton method"); }
  public static <K, V> java.util.Map<K, V> checkedMap(java.util.Map<K, V> a1, java.lang.Class<K> a2, java.lang.Class<V> a3) { throw new RuntimeException("skeleton method"); }
  public static <K, V> java.util.SortedMap<K, V> checkedSortedMap(java.util.SortedMap<K, V> a1, java.lang.Class<K> a2, java.lang.Class<V> a3) { throw new RuntimeException("skeleton method"); }
  public final static <T> @NonNull Set<T> emptySet() { throw new RuntimeException("skeleton method"); }
  public final static <T> @NonNull List<T> emptyList() { throw new RuntimeException("skeleton method"); }
  public final static <K, V> @NonNull Map<K, V> emptyMap() { throw new RuntimeException("skeleton method"); }
  public static <T> @NonNull Set<T> singleton(T a1) { throw new RuntimeException("skeleton method"); }
  public static <T> @NonNull List<T> singletonList(T a1) { throw new RuntimeException("skeleton method"); }
  public static <K, V> @NonNull Map<K, V> singletonMap(K a1, V a2) { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.List<T> nCopies(int a1, T a2) { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.Comparator<T> reverseOrder() { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.Comparator<T> reverseOrder(java.util.Comparator<T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.Enumeration<T> enumeration(java.util.Collection<T> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.ArrayList<T> list(java.util.Enumeration<T> a1) { throw new RuntimeException("skeleton method"); }
  public static int frequency(java.util.Collection<?> a1, java.lang.Object a2) { throw new RuntimeException("skeleton method"); }
  public static boolean disjoint(java.util.Collection<?> a1, java.util.Collection<?> a2) { throw new RuntimeException("skeleton method"); }
  public static <T> boolean addAll(java.util.Collection<? super T> a1, T[] a2) { throw new RuntimeException("skeleton method"); }
  public static <E> java.util.Set<E> newSetFromMap(java.util.Map<E, java.lang.Boolean> a1) { throw new RuntimeException("skeleton method"); }
  public static <T> java.util.Queue<T> asLifoQueue(java.util.Deque<T> a1) { throw new RuntimeException("skeleton method"); }
}
