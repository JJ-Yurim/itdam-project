import { Link } from 'react-router-dom';

// 법무 검토를 거친 확정 약관이 나오면 본문을 교체한다.
export function TermsPage() {
  return (
    <section className="space-y-6">
      <header>
        <Link
          to="/settings/service"
          className="text-sm font-semibold text-stone-500 transition hover:text-stone-800"
        >
          ‹ 서비스 설정
        </Link>
        <h1 className="mt-2 text-2xl font-extrabold text-stone-900">이용약관</h1>
      </header>

      <div className="space-y-5 text-sm leading-6 text-stone-700">
        <section>
          <h2 className="font-bold text-stone-900">제1조 (목적)</h2>
          <p className="mt-1">
            이 약관은 잇담(이하 &quot;서비스&quot;)의 이용 조건과 절차, 이용자와 서비스의 권리와
            의무를 규정하는 것을 목적으로 합니다.
          </p>
        </section>

        <section>
          <h2 className="font-bold text-stone-900">제2조 (콘텐츠의 이용)</h2>
          <p className="mt-1">
            이용자가 작성한 문장은 원정 제출 시 동의한 범위 안에서 공동 책 제작에 사용될 수
            있습니다. 제출한 모든 문장이 공동 책에 수록되는 것은 아니며, 연결성과 분량에 따라
            일부만 수록될 수 있습니다.
          </p>
        </section>

        <section>
          <h2 className="font-bold text-stone-900">제3조 (이용자의 의무)</h2>
          <p className="mt-1">
            이용자는 타인의 권리를 침해하거나 유해한 내용을 포함한 문장을 작성해서는 안 되며,
            안전성 검사에서 거부된 문장은 서비스에 보관되지 않습니다.
          </p>
        </section>

        <p className="text-xs text-stone-400">
          본 약관의 확정본은 서비스 정식 오픈 전에 갱신될 수 있습니다.
        </p>
      </div>
    </section>
  );
}
