import { Link } from 'react-router-dom';

// 법무 검토를 거친 확정 방침이 나오면 본문을 교체한다.
export function PrivacyPolicyPage() {
  return (
    <section className="space-y-6">
      <header>
        <Link
          to="/settings/service"
          className="text-sm font-semibold text-stone-500 transition hover:text-stone-800"
        >
          ‹ 서비스 설정
        </Link>
        <h1 className="mt-2 text-2xl font-extrabold text-stone-900">개인정보처리방침</h1>
      </header>

      <div className="space-y-5 text-sm leading-6 text-stone-700">
        <section>
          <h2 className="font-bold text-stone-900">1. 수집하는 개인정보</h2>
          <p className="mt-1">
            서비스는 회원가입과 이용 과정에서 이메일 주소와 닉네임을 수집합니다. 비밀번호는
            암호화되어 저장되며 원문은 보관하지 않습니다.
          </p>
        </section>

        <section>
          <h2 className="font-bold text-stone-900">2. 개인정보의 이용 목적</h2>
          <p className="mt-1">
            수집한 정보는 회원 식별, 로그인 유지, 창작 활동 기록 관리 목적으로만 사용합니다.
          </p>
        </section>

        <section>
          <h2 className="font-bold text-stone-900">3. 보관 및 파기</h2>
          <p className="mt-1">
            개인정보는 회원 탈퇴 시 지체 없이 파기합니다. 다만 공동 책에 수록된 콘텐츠는 탈퇴
            후 작성자 비식별 처리 상태로 유지될 수 있습니다.
          </p>
        </section>

        <p className="text-xs text-stone-400">
          본 방침의 확정본은 서비스 정식 오픈 전에 갱신될 수 있습니다.
        </p>
      </div>
    </section>
  );
}
