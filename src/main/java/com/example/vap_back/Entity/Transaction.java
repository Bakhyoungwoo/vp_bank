@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private User employee; // 어떤 직원의 거래인지

    private Long amount;        // 결제 금액
    private String merchant;    // 가맹점 (장소)
    private String category;    // 업종 (식비, 교통비 등)
    private double latitude;    // 결제 위치(위도)
    private double longitude;   // 결제 위치(경도)

    private LocalDateTime transactionAt; // 결제 시각
    private boolean isFraud;    // AI 모델이 판단한 이상 여부 (결과 저장)
}