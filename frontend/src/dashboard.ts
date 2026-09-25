export type DashboardItem={id:string;animalId:string;animalName:string;medication:string;dose:string;route:string;scheduledAt:string;administeredAt?:string;administeredBy?:string;status:string}
export type Dashboard={items:DashboardItem[];overdue:number;remaining:number;administered:number}
export async function getDashboard():Promise<Dashboard>{
 const auth=localStorage.getItem('shelterAuth')
 const r=await fetch('/api/dashboard/today',{headers:auth?{Authorization:'Basic '+auth}:{}})
 if(!r.ok)throw new Error(await r.text())
 return r.json()
}
